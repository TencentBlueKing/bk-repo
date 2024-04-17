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
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FAILED_NODE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateRepoStorageUtils.buildThreadPoolExecutor
import com.tencent.bkrepo.job.migrate.utils.TransferDataExecutor
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

@Component
class MigrateFailedNodeExecutor(
    properties: MigrateRepoStorageProperties,
    fileReferenceClient: FileReferenceClient,
    migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    migrateFailedNodeDao: MigrateFailedNodeDao,
    storageService: StorageService,
    executingTaskRecorder: ExecutingTaskRecorder,
    private val transferDataExecutor: TransferDataExecutor,
) : BaseTaskExecutor(
    properties,
    migrateRepoStorageTaskDao,
    migrateFailedNodeDao,
    fileReferenceClient,
    storageService,
    executingTaskRecorder,
) {
    /**
     * 用于重新迁移失败的node
     */
    private val migrateFailedNodeExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("migrate-failed-node-%d")
    }

    /**
     * 迁移migrate与correct两个过程中迁移失败的node，再次失败后需要手动排查原因进行迁移
     *
     * @param context 数据迁移上下文
     *
     * @return 是否开始执行
     */
    override fun execute(context: MigrationContext): Boolean {
        if (migrateFailedNodeExecutor.activeCount == migrateFailedNodeExecutor.maximumPoolSize) {
            return false
        }

        require(context.task.state == CORRECT_FINISHED.name)
        if (!updateState(context.task, MIGRATING_FAILED_NODE.name)) {
            return false
        }
        val newContext = context.copy(task = migrateRepoStorageTaskDao.findById(context.task.id!!)!!.toDto())
        return migrateFailedNodeExecutor.execute(newContext.task, CORRECT_FINISHED.name, MIGRATING_FAILED_NODE.name) {
            doMigrate(newContext)
        }
    }

    private fun doMigrate(context: MigrationContext) {
        val task = context.task
        val projectId = task.projectId
        val repoName = task.repoName
        while (true) {
            val failedNode = findFailedNode(projectId, repoName) ?: break
            val node = convert(failedNode)
            context.incTransferringCount()
            transferDataExecutor.execute {
                try {
                    correctNode(context, node)
                    logger.info("migrate failed node[${node.fullPath}] success, task[${projectId}/${repoName}]")
                    migrateFailedNodeDao.removeById(failedNode.id!!)
                } catch (e: Exception) {
                    logger.error("migrate failed node[${node.fullPath}] failed, task[${projectId}/${repoName}]", e)
                } finally {
                    context.decTransferringCount()
                }
            }
        }
        context.waitAllTransferFinished()
        if (!migrateFailedNodeDao.existsFailedNode(projectId, repoName)) {
            migrateRepoStorageTaskDao.updateState(
                task.id!!, task.state, MIGRATE_FAILED_NODE_FINISHED.name, task.lastModifiedDate
            )
            logger.info("migrate all failed node success, task[${projectId}/${repoName}]")
        } else {
            logger.error("task[${projectId}/${repoName}] still contain migrate failed node that must migrate manually")
        }
    }

    private fun findFailedNode(projectId: String, repoName: String): TMigrateFailedNode? {
        while (true) {
            val failedNode = migrateFailedNodeDao.findOneToRetry(projectId, repoName) ?: return null
            val updateResult = migrateFailedNodeDao.incRetryTimes(
                projectId, repoName, failedNode.fullPath, failedNode.lastModifiedDate
            )
            if (updateResult.modifiedCount == 1L) {
                return failedNode
            } else {
                continue
            }
        }
    }

    private fun convert(failedNode: TMigrateFailedNode) = Node(
        projectId = failedNode.projectId,
        repoName = failedNode.repoName,
        fullPath = failedNode.fullPath,
        size = failedNode.size,
        sha256 = failedNode.sha256,
        md5 = failedNode.md5,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeExecutor::class.java)
    }
}
