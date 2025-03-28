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

package com.tencent.bkrepo.job.migrate.executor

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration.Companion.SERVICE_INSTANCE_ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECTING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.PENDING
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.service.ArchiveJobService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import java.io.FileNotFoundException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class BaseTaskExecutor(
    protected val properties: MigrateRepoStorageProperties,
    protected val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    protected val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val fileReferenceService: FileReferenceService,
    private val storageService: StorageService,
    private val executingTaskRecorder: ExecutingTaskRecorder,
    private val archiveJobService: ArchiveJobService,
) : TaskExecutor {

    @Value(SERVICE_INSTANCE_ID)
    protected lateinit var instanceId: String

    protected open fun executor(): ThreadPoolExecutor? = null

    override fun execute(context: MigrationContext): MigrationContext? {
        val startState = context.task.state
        val (executingState, finishedState) = when (startState) {
            PENDING.name -> Pair(MIGRATING.name, MIGRATE_FINISHED.name)
            MIGRATE_FINISHED.name -> Pair(CORRECTING.name, CORRECT_FINISHED.name)
            // 迁移失败节点时会在根据最终是否还存在失败节点判断是否要转移到结束状态
            CORRECT_FINISHED.name -> Pair(MIGRATING_FAILED_NODE.name, MIGRATING_FAILED_NODE.name)
            else -> throw IllegalStateException("unsupported state[$startState]")
        }

        val newContext = checkExecutable(context, startState, executingState)?.let { prepare(it) } ?: return null
        val executed = executor()?.execute(newContext.task, startState, finishedState) { doExecute(newContext) }
        return if (executed == true) {
            newContext
        } else {
            null
        }
    }

    protected open fun prepare(context: MigrationContext): MigrationContext {
        return context
    }

    protected open fun doExecute(context: MigrationContext) {}

    /**
     * 检查任务是否可执行
     *
     * @param context 待检查任务上下文
     * @param requiredSrcState 期望的源状态
     * @param executingState 目标状态
     *
     * @return 更新后的任务上下文，如果不可执行则返回null
     */
    protected fun checkExecutable(
        context: MigrationContext,
        requiredSrcState: String,
        executingState: String
    ): MigrationContext? {
        with(context.task) {
            require(state == requiredSrcState)
            val executor = executor()
            if (executor != null && executor.activeCount == executor.maximumPoolSize) {
                return null
            }

            return migrateRepoStorageTaskDao
                .updateState(id!!, state, executingState, lastModifiedDate, instanceId)
                ?.let { context.copy(task = it.toDto()) }
        }
    }

    protected fun ThreadPoolExecutor.execute(
        task: MigrateRepoStorageTask,
        oldState: String,
        dstState: String,
        command: Runnable
    ): Boolean {
        with(task) {
            logger.info("Start to $state task[$projectId/$repoName], srcKey[$srcStorageKey], dstKey[$dstStorageKey]")
            val startNanoTime = System.nanoTime()
            try {
                execute {
                    executingTaskRecorder.record(id!!)
                    try {
                        command.run()
                        // 更新任务状态
                        if (state != dstState) {
                            migrateRepoStorageTaskDao.updateState(id, state, dstState, lastModifiedDate)
                            val elapsed = HumanReadable.time(System.nanoTime() - startNanoTime)
                            logger.info("$state task[$projectId/$repoName] success, elapsed[$elapsed]")
                        }
                    } catch (exception: Exception) {
                        val elapsed = HumanReadable.time(System.nanoTime() - startNanoTime)
                        logger.error("$state task[$projectId/$repoName] failed, elapsed[$elapsed]", exception)
                    } finally {
                        executingTaskRecorder.remove(id)
                    }
                }
            } catch (e: RejectedExecutionException) {
                // 回滚状态
                migrateRepoStorageTaskDao.updateState(id!!, state, oldState, lastModifiedDate)
                return false
            }
            return true
        }
    }

    protected fun correctNode(context: MigrationContext, node: Node) {
        checkNode(node)
        val sha256 = node.sha256
        val task = context.task
        val projectId = node.projectId
        val repoName = node.repoName
        // 文件已存在于目标存储则不处理
        val dstFileReferenceExists = fileReferenceService.count(sha256, context.task.dstStorageKey) > 0
        if (storageService.exist(sha256, context.dstCredentials)) {
            if (!dstFileReferenceExists) {
                updateFileReference(context.task.srcStorageKey, context.task.dstStorageKey, sha256)
                logger.info("correct reference[$sha256] success, task[$projectId/$repoName], state[${task.state}]")
            }
            logger.info("file[$sha256] already exists in dst credentials[${context.task.dstStorageKey}]")
            return
        }

        if (dstFileReferenceExists) {
            /*
              可能由于在上传制品时使用的旧存储，而创建Node时由于会重新查一遍仓库的存储凭据而使用新存储
              这种情况会导致目标存储引用大于0但是文件不再目标存储，此时仅迁移存储不修改引用数
             */
            transferData(context, node)
            logger.info("correct success, transfer file[$sha256], task[$projectId/$repoName], state[${task.state}]")
        } else {
            // dst data和reference都不存在，migrate
            migrateNode(context, node)
            logger.info("correct success, migrate file[$sha256], task[$projectId/$repoName], state[${task.state}]")
        }
    }

    protected fun migrateNode(context: MigrationContext, node: Node) {
        checkNode(node)
        val srcStorageKey = context.task.srcStorageKey
        val dstStorageKey = context.task.dstStorageKey
        val sha256 = node.sha256

        // 跨存储迁移数据
        transferData(context, node)

        // 更新引用
        updateFileReference(srcStorageKey, dstStorageKey, sha256)
    }

    open fun close(timeout: Long, unit: TimeUnit) {}

    private fun updateFileReference(srcStorageKey: String?, dstStorageKey: String?, sha256: String) {
        // FileReferenceCleanupJob 会定期清理引用为0的文件数据，所以不需要删除文件数据
        // 迁移前后使用的存储相同时，如果加引用失败减引用成功，可能导致文件误删，因此先增后减
        // new引用计数 +1
        if (!fileReferenceService.increment(sha256, dstStorageKey)) {
            logger.error("Failed to increment file reference[$sha256] on storage[$dstStorageKey].")
        }

        // old引用计数 -1
        if (!fileReferenceService.decrement(sha256, srcStorageKey)) {
            logger.error("Failed to decrement file reference[$sha256] on storage[$srcStorageKey].")
        }
    }

    /**
     * 执行数据迁移
     */
    private fun transferData(context: MigrationContext, node: Node) {
        val archivedFileMigrated = archiveJobService.migrateArchivedFile(context, node)

        // 可能存在同sha256文件被归档后又重新上传的情况，所以被归档的文件也需要尝试迁移数据
        try {
            val throughput = measureThroughput {
                storageService.copy(node.sha256, context.srcCredentials, context.dstCredentials)
                node.size
            }
            // 输出迁移速率
            val msg = "Success to transfer file[${node.sha256}], $throughput, task[${node.projectId}/${node.repoName}]"
            logger.info(msg)
        } catch (e: FileNotFoundException) {
            if (!archivedFileMigrated) {
                throw e
            }
        }
    }

    private fun checkNode(node: Node) {
        with(node) {
            if (sha256 == FAKE_SHA256) {
                throw IllegalArgumentException("can not migrate fake node[$fullPath], task[$projectId/$repoName]")
            }
            if (node.compressed == true) {
                throw IllegalArgumentException("node[$fullPath] was compressed, task[$projectId/$repoName]")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseTaskExecutor::class.java)
    }
}
