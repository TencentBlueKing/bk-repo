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
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration.Companion.SERVICE_INSTANCE_ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class BaseTaskExecutor(
    protected val properties: MigrateRepoStorageProperties,
    protected val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    protected val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val fileReferenceClient: FileReferenceClient,
    private val storageService: StorageService,
    private val executingTaskRecorder: ExecutingTaskRecorder,
) : TaskExecutor {

    @Value(SERVICE_INSTANCE_ID)
    protected lateinit var instanceId: String

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
        val sha256 = node.sha256
        // 文件已存在于目标存储则不处理
        if (storageService.exist(sha256, context.dstCredentials)) {
            logger.info("file[$sha256] already exists in dst credentials[${context.task.dstStorageKey}]")
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
        }
    }

    protected fun migrateNode(context: MigrationContext, node: Node) {
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
        logger.info("Success to migrate file[$sha256].")
    }

    protected fun updateState(task: MigrateRepoStorageTask, dstState: String): Boolean {
        val updateResult = migrateRepoStorageTaskDao.updateState(
            task.id!!, task.state, dstState, task.lastModifiedDate, instanceId
        )
        return updateResult.modifiedCount != 0L
    }

    protected fun saveMigrateFailedNode(taskId: String, node: Node) {
        if (migrateFailedNodeDao.existsFailedNode(node.projectId, node.repoName, node.fullPath)) {
            return
        }

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
            } catch (ignore: DuplicateKeyException) {
            }
        }
    }

    open fun close(timeout: Long, unit: TimeUnit) {}

    /**
     * 执行数据迁移
     */
    private fun transferData(context: MigrationContext, node: Node) {
        val throughput = measureThroughput {
            storageService.copy(node.sha256, context.srcCredentials, context.dstCredentials)
            node.size
        }
        // 输出迁移速率
        logger.info("Success to transfer file[${node.sha256}], $throughput, task[${node.projectId}/${node.repoName}]")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseTaskExecutor::class.java)
    }
}
