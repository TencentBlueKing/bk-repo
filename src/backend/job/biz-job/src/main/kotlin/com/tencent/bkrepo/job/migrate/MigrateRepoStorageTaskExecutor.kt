/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.migrate

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration.Companion.SERVICE_INSTANCE_ID
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECTING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.FINISHING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FAILED_NODE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.PENDING
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit

/**
 * 迁移仓库存储任务执行器
 */
@Component
class MigrateRepoStorageTaskExecutor(
    private val storageProperties: StorageProperties,
    private val properties: MigrateRepoStorageProperties,
    private val mongoTemplate: MongoTemplate,
    private val repositoryClient: RepositoryClient,
    private val fileReferenceClient: FileReferenceClient,
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val storageService: StorageService,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
) {
    @Value(SERVICE_INSTANCE_ID)
    private lateinit var instanceId: String

    /**
     * 任务执行线程池，用于提交node迁移任务到[transferDataExecutor]
     */
    private val migrateExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("migrate-repo-storage-%d")
    }

    /**
     * 用于执行数据矫正的线程池
     */
    private val correctExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("correct-repo-storage-%d")
    }

    /**
     * 用于重新迁移失败的node
     */
    private val migrateFailedNodeExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("migrate-failed-node-%d")
    }

    /**
     * 实际执行数据迁移的线程池
     */
    private val transferDataExecutor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            properties.nodeConcurrency,
            properties.nodeConcurrency,
            0L,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("migrate-node-%d").build(),
            CallerRunsPolicy()
        )
    }

    /**
     * 当前正在本实例中执行的任务，key为任务状态，value为任务id集合
     */
    private val executingTasks: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()

    /**
     * 回滚因进程重启或其他原因导致中断的任务状态
     */
    fun rollbackInterruptedTaskState() {
        migrateRepoStorageTaskDao.timeoutTasks(instanceId, properties.timeout).forEach {
            if (executingTasks[it.state]?.contains(it.id!!) != true) {
                val rollbackState = when (it.state) {
                    MIGRATING.name -> PENDING.name
                    CORRECTING.name -> MIGRATE_FINISHED.name
                    MIGRATING_FAILED_NODE.name -> CORRECT_FINISHED.name
                    FINISHING.name -> MIGRATE_FAILED_NODE_FINISHED.name
                    else -> throw IllegalStateException("cant rollback state[${it.state}]")
                }
                // 任务之前在本实例内执行，但可能由于进程重启或其他原因而中断，需要重置状态
                migrateRepoStorageTaskDao.save(it.copy(state = rollbackState))
            }
        }
    }

    /**
     * 执行任务
     *
     * @param task 仓库存储迁移任务
     *
     * @return 是否开始执行
     */
    fun execute(task: MigrateRepoStorageTask): Boolean {
        with(task) {
            // 线程池满时不执行任务
            if (executorFull(state)) {
                return false
            }

            return when (state) {
                PENDING.name -> migrate(task)
                MIGRATE_FINISHED.name -> correct(task)
                CORRECT_FINISHED.name -> migrateFailedNode(task)
                MIGRATE_FAILED_NODE_FINISHED.name -> finish(task)
                else -> throw IllegalStateException("unsupported state[$state], task[$projectId/$repoName]")
            }
        }
    }

    /**
     * 执行迁移任务,对startTime之前创建的node进行迁移
     *
     * @param task 数据迁移任务
     *
     * @return 是否开始执行
     */
    private fun migrate(task: MigrateRepoStorageTask): Boolean {
        val context = prepare(task) ?: return false
        return migrateExecutor.execute(context.task, PENDING.name, MIGRATE_FINISHED.name) {
            val projectId = context.task.projectId
            val repoName = context.task.repoName
            val taskId = context.task.id!!
            val iterator = NodeIterator(context.task, mongoTemplate)
            val totalCount = iterator.totalCount()

            // 更新待迁移制品总数
            migrateRepoStorageTaskDao.updateTotalCount(taskId, totalCount)
            iterator.forEach { node ->
                context.incTransferringCount()
                transferDataExecutor.execute {
                    try {
                        // 迁移制品
                        migrateNode(context, node)
                    } finally {
                        context.decTransferringCount()
                        // 更新任务进度
                        val iteratedCount = iterator.iteratedCount()
                        if (iteratedCount % properties.updateProgressInterval == 0L) {
                            logger.info("migrate repo[${projectId}/${repoName}], storage progress[$iteratedCount/$totalCount]")
                            migrateRepoStorageTaskDao.updateMigratedCount(taskId, iteratedCount)
                        }
                    }
                }
            }
            context.waitAllTransferFinished()
            migrateRepoStorageTaskDao.updateMigratedCount(taskId, iterator.iteratedCount())
        }
    }

    /**
     * 执行数据矫正
     *
     * @param task 数据迁移任务
     *
     * @return 是否开始执行
     */
    private fun correct(task: MigrateRepoStorageTask): Boolean {
        require(task.state == MIGRATE_FINISHED.name)
        if (!updateState(task, CORRECTING.name)) {
            return false
        }
        val context = buildContext(migrateRepoStorageTaskDao.findById(task.id!!)!!.toDto())
        return correctExecutor.execute(context.task, MIGRATE_FINISHED.name, CORRECT_FINISHED.name) {
            doCorrect(context)
        }
    }

    /**
     * 迁移migrate与correct两个过程中迁移失败的node，再次失败后需要手动排查原因进行迁移
     *
     * @param task 数据迁移任务
     *
     * @return 是否开始执行
     */
    private fun migrateFailedNode(task: MigrateRepoStorageTask): Boolean {
        require(task.state == CORRECT_FINISHED.name)
        if (!updateState(task, MIGRATING_FAILED_NODE.name)) {
            return false
        }
        val updatedTask = migrateRepoStorageTaskDao.findById(task.id!!)!!.toDto()
        val context = buildContext(updatedTask)
        val projectId = updatedTask.projectId
        val repoName = updatedTask.repoName
        return migrateFailedNodeExecutor.execute(updatedTask, CORRECT_FINISHED.name, MIGRATING_FAILED_NODE.name) {
            while (true) {
                val failedNode = migrateFailedNodeDao.findOneToRetry(projectId, repoName) ?: break
                val node = Node(
                    projectId = failedNode.projectId,
                    repoName = failedNode.repoName,
                    fullPath = failedNode.fullPath,
                    size = failedNode.size,
                    sha256 = failedNode.sha256,
                    md5 = failedNode.md5,
                )
                context.incTransferringCount()
                transferDataExecutor.execute {
                    try {
                        correctNode(context, node)
                        val fullPath = failedNode.fullPath
                        logger.info("migrate failed node[$fullPath] success, task[${projectId}/${repoName}]")
                        migrateFailedNodeDao.removeById(failedNode.id!!)
                    } finally {
                        context.decTransferringCount()
                    }
                }
            }
            context.waitAllTransferFinished()
            if (!migrateFailedNodeDao.existsFailedNode(projectId, repoName)) {
                migrateRepoStorageTaskDao.updateState(
                    updatedTask.id!!, updatedTask.state, MIGRATE_FAILED_NODE_FINISHED.name, updatedTask.lastModifiedDate
                )
                logger.info("migrate all failed node success, task[${projectId}/${repoName}]")
            } else {
                logger.error(
                    "task[${projectId}/${repoName}] still contain migrate failed node that must migrate manually"
                )
            }
        }
    }

    /**
     * 迁移任务执行结束后对相关资源进行清理
     */
    private fun finish(task: MigrateRepoStorageTask): Boolean {
        with(task) {
            if (!updateState(task, FINISHING.name)) {
                return false
            }
            logger.info("migrate finished, task[$task]")
            repositoryClient.unsetOldStorageCredentialsKey(projectId, repoName)
            migrateRepoStorageTaskDao.removeById(id!!)
            logger.info("clean migrate task[${projectId}/${repoName}] success")
            return true
        }
    }

    private fun ThreadPoolExecutor.execute(
        task: MigrateRepoStorageTask,
        oldState: String,
        dstState: String,
        command: Runnable
    ): Boolean {
        with(task) {
            logger.info("Start to $state task[$projectId/$repoName], srcKey[$srcStorageKey], dstKey[$dstStorageKey]")
            val startNanoTime = System.nanoTime()
            executingTasks.getOrPut(state) { ConcurrentHashSet() }.add(id!!)
            try {
                execute {
                    try {
                        command.run()
                        // 更新任务状态
                        if (state != dstState) {
                            migrateRepoStorageTaskDao.updateState(id, state, dstState, lastModifiedDate)
                            val elapsed = System.nanoTime() - startNanoTime
                            logger.info("$state task[$projectId/$repoName] success, elapsed[$elapsed ns]")
                        }
                    } catch (exception: Exception) {
                        val elapsed = System.nanoTime() - startNanoTime
                        logger.error("$state task[$projectId/$repoName] failed, elapsed[$elapsed ns]", exception)
                    } finally {
                        executingTasks[state]!!.remove(id)
                    }
                }
            } catch (e: RejectedExecutionException) {
                // 回滚状态
                executingTasks[state]!!.remove(id)
                migrateRepoStorageTaskDao.updateState(id, state, oldState, lastModifiedDate)
                return false
            }
            return true
        }
    }

    private fun migrateNode(context: MigrationContext, node: Node): Long {
        val srcStorageKey = context.task.srcStorageKey
        val dstStorageKey = context.task.dstStorageKey
        val sha256 = node.sha256

        // 跨存储迁移数据
        transferData(context, node)

        // FileReferenceCleanupJob 会定期清理引用为0的文件数据，所以不需要删除文件数据
        // old引用计数 -1
        if (fileReferenceClient.decrement(sha256, srcStorageKey).data != true) {
            logger.error("Failed to decrement file reference[$sha256] on storage[$srcStorageKey].")
        }
        // new引用计数 +1
        if (fileReferenceClient.increment(sha256, dstStorageKey).data != true) {
            logger.error("Failed to increment file reference[$sha256] on storage[$dstStorageKey].")
        }
        return node.size
    }

    private fun doCorrect(context: MigrationContext) {
        with(context) {
            val iterator = NodeIterator(task, mongoTemplate)
            iterator.forEach { node ->
                context.incTransferringCount()
                transferDataExecutor.execute {
                    try {
                        correctNode(context, node)
                    } finally {
                        context.decTransferringCount()
                    }
                }
            }
            context.waitAllTransferFinished()
        }
    }

    private fun correctNode(context: MigrationContext, node: Node) {
        val sha256 = node.sha256
        // 文件已存在于目标存储则不处理
        if (storageService.exist(sha256, context.dstCredentials)) {
            return
        }

        if (fileReferenceClient.count(sha256, context.task.dstStorageKey).data!! > 0) {
            /*
              可能由于在上传制品时使用的旧存储，而创建Node时由于会重新查一遍仓库的存储凭据而使用新存储
              这种情况会导致目标存储引用大于0但是文件不再目标存储，此时仅迁移存储不修改引用数
             */
            transferData(context, node)
            logger.info("Success to correct file[$sha256].")
        } else {
            // dst data和reference都不存在，migrate
            migrateNode(context, node)
            logger.info("Success to migrate file[$sha256].")
        }
    }

    /**
     * 做一些任务开始执行前的准备工作
     */
    private fun prepare(task: MigrateRepoStorageTask): MigrationContext? {
        require(task.state == PENDING.name)
        if (!updateState(task, MIGRATING.name)) {
            return null
        }

        val repo = repositoryClient.getRepoDetail(task.projectId, task.repoName).data!!
        if (repo.storageCredentials?.key != task.dstStorageKey) {
            // 修改repository配置，保证之后上传的文件直接保存到新存储实例中，文件下载时，当前实例找不到的情况下会去默认存储找
            // 任务首次执行才更新仓库配置，从上次中断点继续执行时不需要重复更新
            val startDate = LocalDateTime.now()
            migrateRepoStorageTaskDao.updateStartDate(task.id!!, startDate)
            logger.info("update migrate task of [${task.projectId}/${task.repoName}] startDate[$startDate]")
            repositoryClient.updateStorageCredentialsKey(task.projectId, task.repoName, task.dstStorageKey)
            logger.info("update repo[${task.projectId}/${task.repoName}] dstStorageKey[${task.dstStorageKey}]")
        }
        return buildContext(migrateRepoStorageTaskDao.findById(task.id!!)!!.toDto())
    }

    /**
     * 执行数据迁移
     */
    private fun transferData(context: MigrationContext, node: Node) {
        val sha256 = node.sha256
        try {
            val throughput = retry(RETRY_COUNT) {
                measureThroughput {
                    storageService.copy(sha256, context.srcCredentials, context.dstCredentials)
                    node.size
                }
            }
            // 输出迁移速率
            logger.info("Success to transfer file[$sha256], $throughput, task[${node.projectId}/${node.repoName}]")
        } catch (e: Exception) {
            logger.error("transfer node[${node.projectId}/${node.repoName}/${node.fullPath}] failed, [$sha256]", e)
            saveMigrateFailedNode(context.task.id!!, node)
            throw e
        }
    }

    private fun updateState(task: MigrateRepoStorageTask, dstState: String): Boolean {
        val updateResult = migrateRepoStorageTaskDao.updateState(
            task.id!!, task.state, dstState, task.lastModifiedDate, instanceId
        )
        if (updateResult.modifiedCount == 0L) {
            logger.info("task[${task.projectId}/${task.repoName}] was executing by other thread")
            return false
        }
        return true
    }

    private fun saveMigrateFailedNode(taskId: String, node: Node) {
        val now = LocalDateTime.now()
        with(node) {
            try {
                migrateFailedNodeDao.insert(
                    TMigrateFailedNode(
                        id = null,
                        createdDate = now,
                        lastModifiedDate = now,
                        taskId = taskId,
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = fullPath,
                        sha256 = sha256,
                        md5 = md5,
                        size = size,
                        retryTimes = 0
                    )
                )
            } catch (e: DuplicateKeyException) {
                migrateFailedNodeDao.incRetryTimes(node.projectId, node.repoName, node.fullPath)
            }
        }
    }

    private fun getStorageCredentials(key: String?): StorageCredentials {
        return if (key == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(key)!!
        }
    }

    /**
     * 检查指定状态的任务执行线程池是否已满
     *
     * @param state 任务状态
     *
     * @return True表示线程池已满， False表示线程未满
     */
    private fun executorFull(state: String): Boolean {
        val executor = when (state) {
            PENDING.name -> migrateExecutor
            MIGRATE_FINISHED.name -> correctExecutor
            CORRECT_FINISHED.name -> migrateFailedNodeExecutor
            MIGRATE_FAILED_NODE_FINISHED.name -> null
            else -> throw IllegalStateException("unsupported state[$state]")
        }

        return executor != null && executor.activeCount == executor.maximumPoolSize
    }

    private fun buildContext(task: MigrateRepoStorageTask): MigrationContext {
        val srcCredentials = getStorageCredentials(task.srcStorageKey)
        val dstCredentials = getStorageCredentials(task.dstStorageKey)
        return MigrationContext(
            task = task,
            srcCredentials = srcCredentials,
            dstCredentials = dstCredentials,
        )
    }

    private fun buildThreadPoolExecutor(
        nameFormat: String,
        size: Int = Runtime.getRuntime().availableProcessors(),
    ) = ThreadPoolExecutor(
        size,
        size,
        0L,
        TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        ThreadFactoryBuilder().setNameFormat(nameFormat).build(),
        AbortPolicy()
    )

    companion object {
        private const val RETRY_COUNT = 3
        private val logger = LoggerFactory.getLogger(MigrateRepoStorageTaskExecutor::class.java)
    }
}
