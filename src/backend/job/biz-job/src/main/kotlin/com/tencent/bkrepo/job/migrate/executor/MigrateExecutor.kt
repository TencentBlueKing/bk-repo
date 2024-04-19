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

import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateRepoStorageUtils.buildThreadPoolExecutor
import com.tencent.bkrepo.job.migrate.utils.MigratedTaskNumberPriorityQueue
import com.tencent.bkrepo.job.migrate.utils.NodeIterator
import com.tencent.bkrepo.job.migrate.utils.TransferDataExecutor
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class MigrateExecutor(
    properties: MigrateRepoStorageProperties,
    fileReferenceClient: FileReferenceClient,
    migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    migrateFailedNodeDao: MigrateFailedNodeDao,
    storageService: StorageService,
    executingTaskRecorder: ExecutingTaskRecorder,
    private val transferDataExecutor: TransferDataExecutor,
    private val repositoryClient: RepositoryClient,
    private val mongoTemplate: MongoTemplate
) : BaseTaskExecutor(
    properties,
    migrateRepoStorageTaskDao,
    migrateFailedNodeDao,
    fileReferenceClient,
    storageService,
    executingTaskRecorder,
) {
    /**
     * 任务执行线程池，用于提交node迁移任务到[transferDataExecutor]
     */
    private val migrateExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("migrate-repo-storage-%d")
    }

    override fun executor() = migrateExecutor

    override fun close(timeout: Long, unit: TimeUnit) {
        migrateExecutor.shutdown()
        migrateExecutor.awaitTermination(timeout, unit)
    }

    override fun doExecute(context: MigrationContext) {
        val projectId = context.task.projectId
        val repoName = context.task.repoName
        val taskId = context.task.id!!
        val iterator = NodeIterator(context.task, mongoTemplate)
        val totalCount = iterator.totalCount()
        val migratedNumberQueue = MigratedTaskNumberPriorityQueue()
        var iteratedCount = 0L

        // 更新待迁移制品总数
        migrateRepoStorageTaskDao.updateTotalCount(taskId, totalCount)

        // 遍历迁移制品
        iterator.forEach { node ->
            val taskNumber = ++iteratedCount
            context.incTransferringCount()
            transferDataExecutor.execute {
                try {
                    // 迁移制品
                    migrateNode(context, node)
                } catch (e: Exception) {
                    saveMigrateFailedNode(taskId, node)
                    logger.error("migrate node[${node.fullPath}] failed, task[$projectId/$repoName]", e)
                } finally {
                    // 保存完成的任务序号
                    migratedNumberQueue.offer(taskNumber)
                    // 更新任务进度，用于进程重启时从断点继续迁移
                    if (taskNumber % properties.updateProgressInterval == 0L) {
                        val migratedCount = migratedNumberQueue.updateLeftMax()
                        logger.info("migrate repo[${projectId}/${repoName}], progress[$migratedCount/$totalCount]")
                        migrateRepoStorageTaskDao.updateMigratedCount(taskId, migratedCount)
                    }
                    context.decTransferringCount()
                }
            }
        }

        // 等待所有数传输完成
        context.waitAllTransferFinished()
        migrateRepoStorageTaskDao.updateMigratedCount(taskId, iteratedCount)
    }

    /**
     * 做一些任务开始执行前的准备工作
     */
    override fun prepare(context: MigrationContext): MigrationContext {
        val task = context.task

        val repo = repositoryClient.getRepoDetail(task.projectId, task.repoName).data!!
        // 任务首次执行才更新仓库配置，从上次中断点继续执行时不需要重复更新
        if (repo.storageCredentials?.key != task.dstStorageKey) {
            val startDate = LocalDateTime.now()
            migrateRepoStorageTaskDao.updateStartDate(task.id!!, startDate)
            logger.info("update migrate task of [${task.projectId}/${task.repoName}] startDate[$startDate]")
            // 修改repository配置，保证之后上传的文件直接保存到新存储实例中，文件下载时，当前实例找不到的情况下会去默认存储找
            repositoryClient.updateStorageCredentialsKey(task.projectId, task.repoName, task.dstStorageKey)
            logger.info("update repo[${task.projectId}/${task.repoName}] dstStorageKey[${task.dstStorageKey}]")
        }
        return context.copy(task = migrateRepoStorageTaskDao.findById(task.id!!)!!.toDto())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateExecutor::class.java)
    }
}
