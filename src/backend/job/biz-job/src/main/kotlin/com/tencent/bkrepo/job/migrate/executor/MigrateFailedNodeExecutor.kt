/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.migrate.Constant.MAX_MIGRATE_FAILED_RETRY_TIMES
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FAILED_NODE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateRepoStorageUtils.buildThreadPoolExecutor
import com.tencent.bkrepo.job.migrate.utils.TransferDataExecutor
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class MigrateFailedNodeExecutor(
    properties: MigrateRepoStorageProperties,
    fileReferenceService: FileReferenceService,
    migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    migrateFailedNodeDao: MigrateFailedNodeDao,
    storageService: StorageService,
    executingTaskRecorder: ExecutingTaskRecorder,
    migrateArchivedFileService: MigrateArchivedFileService,
    blockNodeService: BlockNodeService,
    private val transferDataExecutor: TransferDataExecutor,
    private val migrateFailedNodeFixer: MigrateFailedNodeFixer,
    private val nodeDao: NodeDao,
    mongoTemplate: MongoTemplate,
) : BaseTaskExecutor(
    properties,
    migrateRepoStorageTaskDao,
    migrateFailedNodeDao,
    fileReferenceService,
    storageService,
    executingTaskRecorder,
    migrateArchivedFileService,
    blockNodeService,
    mongoTemplate,
) {
    /**
     * 用于重新迁移失败的node
     */
    private val migrateFailedNodeExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("migrate-failed-node-%d")
    }

    override fun executor() = migrateFailedNodeExecutor

    override fun close(timeout: Long, unit: TimeUnit) {
        migrateFailedNodeExecutor.shutdown()
        migrateFailedNodeExecutor.awaitTermination(timeout, unit)
    }

    override fun doExecute(context: MigrationContext) {
        val task = context.task
        val projectId = task.projectId
        val repoName = task.repoName
        while (true) {
            val failedNode = migrateFailedNodeDao.findOneToRetry(projectId, repoName) ?: break
            val node = convert(failedNode)
            context.incTransferringCount()
            transferDataExecutor.execute(node) {
                try {
                    correctNode(context, node)
                    logger.info("migrate failed node[${node.fullPath}] success, task[${projectId}/${repoName}]")
                    migrateFailedNodeDao.removeById(failedNode.id!!)
                } catch (e: Exception) {
                    migrateFailedNodeDao.resetMigrating(failedNode.id!!)
                    logger.error("migrate failed node[${node.fullPath}] failed, task[${projectId}/${repoName}]", e)
                    if (failedNode.retryTimes >= MAX_MIGRATE_FAILED_RETRY_TIMES) {
                        logger.info("try to fix node[${node.fullPath}] failed, task[${projectId}/${repoName}]")
                        migrateFailedNodeFixer.fix(failedNode)
                    }
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

    private fun convert(failedNode: TMigrateFailedNode): Node {
        val criteria = Node::projectId.isEqualTo(failedNode.projectId).and(ID).isEqualTo(failedNode.nodeId)
        val node = nodeDao.findOne(Query(criteria))
        if (node != null && node.fullPath != failedNode.fullPath) {
            logger.warn(
                "node[${failedNode.nodeId}] fullPath changed from [${failedNode.fullPath}] to [${node.fullPath}], " +
                    "task[${failedNode.projectId}/${failedNode.repoName}]"
            )
        }
        return Node(
            id = failedNode.nodeId,
            projectId = failedNode.projectId,
            repoName = failedNode.repoName,
            // 节点可能在迁移失败后被并发rename，分块已随rename迁移到新路径，
            // 因此优先以库中当前节点的fullPath为准，避免用失败时记录的旧路径查不到分块
            fullPath = node?.fullPath ?: failedNode.fullPath,
            size = failedNode.size,
            sha256 = failedNode.sha256,
            md5 = failedNode.md5,
            createdDate = node?.createdDate,
            deleted = node?.deleted,
            archived = node?.archived,
            compressed = node?.compressed,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeExecutor::class.java)
    }
}
