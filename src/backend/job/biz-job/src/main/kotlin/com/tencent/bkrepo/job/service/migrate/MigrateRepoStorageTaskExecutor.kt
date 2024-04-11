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

package com.tencent.bkrepo.job.service.migrate

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.pojo.MigrateRepoStorageTaskState.EXECUTING
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
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
) {
    /**
     * 任务执行线程池，用于提交node迁移任务到[migrateExecutor]
     */
    private val taskExecutor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            0L,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("migrate-repo-storage-task-%d").build(),
            AbortPolicy()
        )
    }

    /**
     * 实际执行数据迁移的线程池
     */
    private val migrateExecutor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            properties.nodeConcurrency,
            properties.nodeConcurrency,
            0L,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("migrate-node-%d").build(),
            AbortPolicy()
        )
    }

    /**
     * 执行迁移任务
     *
     * @param task 数据迁移任务
     *
     * @return 是否开始执行
     */
    fun migrate(task: MigrateRepoStorageTask): Boolean {
        with(task) {
            logger.info(
                "Start to execute migrate task[$projectId/$repoName], srcKey[$srcStorageKey], dstKey[$dstStorageKey]"
            )
            val context = prepare(task) ?: return false
            try {
                taskExecutor.submit {
                    try {
                        doMigrate(context)
                        correct(task)
                    } catch (exception: Exception) {
                        logger.error("Migrate task[$projectId/$repoName] failed.", exception)
                    }
                }
            } catch (e: RejectedExecutionException) {
                return false
            }
            return true
        }
    }

    /**
     * 对startTime之前创建的node执行迁移
     */
    private fun doMigrate(context: MigrationContext) {
        with(context) {
            val startNanoTime = System.nanoTime()
            val iterator = NodeIterator(context.task, mongoTemplate)
            // 更新待迁移制品总数
            migrateRepoStorageTaskDao.updateTotalCount(task.id!!, iterator.totalCount())

            iterator.forEach {
                try {
                    // 迁移制品
                    val throughput = measureThroughput {
                        migrateNode(it, context.task.srcStorageKey, context.task.dstStorageKey)
                    }
                    // 输出迁移速率
                    logger.info(
                        "Success to migrate file[${it.sha256}], " +
                                "$throughput, task[${task.projectId}/${task.repoName}]"
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    logger.error("Failed to migrate file[${it.sha256}], task[${task.projectId}/${task.repoName}]", e)
                    failedCount.incrementAndGet()
                } finally {
                    totalCount.incrementAndGet()
                }

                val iteratedCount = iterator.iteratedCount()
                // 更新任务进度
                if (iteratedCount % properties.updateProgressInterval == 0L) {
                    logger.info(
                        "migrate repo[${task.projectId}/${task.repoName}]," +
                                " storage progress[$iteratedCount/${iterator.totalCount()}]"
                    )
                    migrateRepoStorageTaskDao.updateMigratedCount(task.id!!, iteratedCount)
                }
            }

            val migrateElapsed = System.nanoTime() - startNanoTime
            logger.info(
                "Complete migrate old files, " +
                        "project: ${task.projectId}, repo: ${task.repoName}, key: ${task.dstStorageKey}, " +
                        "total: $totalCount, success: $successCount, failed: $failedCount, duration $migrateElapsed ns"
            )
        }
    }

    private fun migrateNode(node: Node, srcStorageKey: String?, dstStorageKey: String?): Long {
        val srcCredentials = getStorageCredentials(srcStorageKey)
        val dstCredentials = getStorageCredentials(dstStorageKey)
        val sha256 = node.sha256

        // 源文件不存在时不执行迁移，忽略该文件
        if (!storageService.exist(sha256, srcCredentials)) {
            logger.error("File data [$sha256] not found in src[$srcStorageKey]")
            return 0L
        }

        // 跨bucket copy
        retry(RETRY_COUNT) {
            storageService.copy(node.sha256, srcCredentials, dstCredentials)
        }

        // FileReferenceCleanupJob 会定期清理引用为0的文件数据，所以不需要删除文件数据
        // old引用计数 -1
        if (fileReferenceClient.decrement(sha256, srcStorageKey).data != true) {
            logger.error("Failed to decrement file reference[$sha256].")
        }
        // new引用计数 +1
        if (fileReferenceClient.increment(sha256, dstStorageKey).data != true) {
            logger.error("Failed to decrement file reference[$sha256].")
        }
        return node.size
    }

    private fun correct(task: MigrateRepoStorageTask) {
        val context = CorrectionContext(task)
        TODO()
    }

    /**
     * 做一些任务开始执行前的准备工作
     */
    private fun prepare(task: MigrateRepoStorageTask): MigrationContext? {
        if (migrateRepoStorageTaskDao.updateState(task.id!!, task.state, EXECUTING.name).modifiedCount == 0L) {
            val msg = "migrate task [${task.projectId}/${task.repoName}] already executed by other thread"
            logger.warn(msg)
            return null
        }

        return if (task.startDate == null) {
            // 修改repository配置，保证之后上传的文件直接保存到新存储实例中，文件下载时，当前实例找不到的情况下会去默认存储找
            // 任务首次执行才更新仓库配置，从上次中断点继续执行时不需要重复更新
            val startDate = LocalDateTime.now()
            migrateRepoStorageTaskDao.updateStartDate(task.id!!, startDate)
            logger.info("update migrate task of [${task.projectId}/${task.repoName}] startDate[$startDate]")
            repositoryClient.updateStorageCredentialsKey(task.projectId, task.repoName, task.dstStorageKey)
            logger.info("update repo[${task.projectId}/${task.repoName}] dstStorageKey[${task.dstStorageKey}]")
            MigrationContext(task.copy(startDate = startDate, state = EXECUTING.name))
        } else {
            MigrationContext(task.copy(state = EXECUTING.name))
        }
    }

    private fun getStorageCredentials(key: String?): StorageCredentials {
        return if (key == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(key)!!
        }
    }

    companion object {
        private const val RETRY_COUNT = 3
        private val logger = LoggerFactory.getLogger(MigrateRepoStorageTaskExecutor::class.java)
    }
}
