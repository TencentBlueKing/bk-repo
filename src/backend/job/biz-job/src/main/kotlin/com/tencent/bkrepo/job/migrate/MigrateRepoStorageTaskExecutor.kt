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
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.EXECUTING
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.CorrectionContext
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
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
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
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
            com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.info(
                "Start to execute migrate task[$projectId/$repoName], srcKey[$srcStorageKey], dstKey[$dstStorageKey]"
            )
            val context = prepare(task) ?: return false
            try {
                taskExecutor.submit {
                    try {
                        doMigrate(context)
                        correct(task)
                    } catch (exception: Exception) {
                        com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.error("Migrate task[$projectId/$repoName] failed.", exception)
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

            iterator.forEach { node ->
                try {
                    // 迁移制品
                    val throughput = measureThroughput {
                        migrateNode(node, context.task.srcStorageKey, context.task.dstStorageKey)
                    }
                    // 输出迁移速率
                    com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.info(
                        "Success to migrate file[${node.sha256}], $throughput, task[${task.projectId}/${task.repoName}]"
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.error("Failed to migrate file[${node.sha256}], task[${task.projectId}/${task.repoName}]", e)
                    saveMigrateFailedNode(node)
                    failedCount.incrementAndGet()
                } finally {
                    totalCount.incrementAndGet()
                }

                val iteratedCount = iterator.iteratedCount()
                // 更新任务进度
                if (iteratedCount % properties.updateProgressInterval == 0L) {
                    com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.info(
                        "migrate repo[${task.projectId}/${task.repoName}]," +
                                " storage progress[$iteratedCount/${iterator.totalCount()}]"
                    )
                    migrateRepoStorageTaskDao.updateMigratedCount(task.id, iteratedCount)
                }
            }

            val migrateElapsed = System.nanoTime() - startNanoTime
            com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.info(
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

        // 跨bucket copy
        retry(RETRY_COUNT) {
            storageService.copy(node.sha256, srcCredentials, dstCredentials)
        }

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

    private fun correct(task: MigrateRepoStorageTask) {
        val context = CorrectionContext(task)
        with(context) {
            val startNanoTime = System.nanoTime()
            val iterator = NodeIterator(task, mongoTemplate, true)
            iterator.forEach { node ->
                try {
                    correctNode(context, node)
                } catch (exception: Exception) {
                    saveMigrateFailedNode(node)
                    com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.error("Failed to check file[$${node.sha256}].", exception)
                    correctFailedCount.incrementAndGet()
                } finally {
                    correctTotalCount.incrementAndGet()
                }
            }
            val elapsed = System.nanoTime() - startNanoTime
            com.tencent.bkrepo.job.migrate.MigrateRepoStorageTaskExecutor.logger.info(
                "Complete check new created files, " +
                        "projectId: ${task.projectId}, repoName: ${task.repoName}, key: ${task.dstStorageKey}, " +
                        "total: $correctTotalCount, correct: $correctSuccessCount, migrate: $correctMigrateCount, " +
                        "missing data: $dataMissingCount, failed: $correctFailedCount, duration $elapsed ns."
            )
        }
    }

    private fun correctNode(context: CorrectionContext, node: Node) {
        val srcCredentials = getStorageCredentials(context.task.srcStorageKey)
        val dstCredentials = getStorageCredentials(context.task.dstStorageKey)
        val sha256 = node.sha256
        // 文件已存在于目标存储则不处理
        if (storageService.exist(sha256, dstCredentials)) {
            return
        }

        if (fileReferenceClient.count(sha256, context.task.dstStorageKey).data!! > 0) {
            /*
              可能由于在上传制品时使用的旧存储，而创建Node时由于会重新查一遍仓库的存储凭据而使用新存储
              这种情况会导致目标存储引用大于0但是文件不再目标存储，此时仅迁移存储不修改引用数
             */
            retry(RETRY_COUNT) { storageService.copy(sha256, srcCredentials, dstCredentials) }
            context.correctSuccessCount.incrementAndGet()
            logger.info("Success to correct file[$sha256].")
        } else {
            // dst data和reference都不存在，migrate
            migrateNode(node, context.task.srcStorageKey, context.task.dstStorageKey)
            context.correctMigrateCount.incrementAndGet()
            logger.info("Success to migrate file[$sha256].")
        }
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

    private fun saveMigrateFailedNode(node: Node) {
        val now = LocalDateTime.now()
        with(node) {
            migrateFailedNodeDao.insert(
                TMigrateFailedNode(
                    id = null,
                    createdDate = now,
                    lastModifiedDate = now,
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    sha256 = sha256,
                    md5 = md5,
                    size = size,
                    retryTimes = 0
                )
            )
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
