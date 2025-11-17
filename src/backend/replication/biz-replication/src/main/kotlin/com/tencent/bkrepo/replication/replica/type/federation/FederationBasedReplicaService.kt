/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.pojo.task.TaskExecuteType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.replica.type.AbstractReplicaService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import org.springframework.stereotype.Component

/**
 * 基于事件消息的实时任务同步器
 */
@Component
class FederationBasedReplicaService(
    replicaRecordService: ReplicaRecordService,
    localDataManager: LocalDataManager,
    replicaFailureRecordDao: ReplicaFailureRecordDao,
) : AbstractReplicaService(replicaRecordService, localDataManager, replicaFailureRecordDao) {

    override fun replica(context: ReplicaContext) {
        // 全量同步的场景需要同步已删除节点
        replicaTaskObjects(context)
    }

    /**
     * 是否包含所有仓库数据
     */
    override fun includeAllData(context: ReplicaContext): Boolean {
        try {
            context.event != null
            return false
        } catch (e: Exception) {
            return true
        }
    }

    override fun replicaTaskObjectConstraints(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 只有非third party集群支持该消息
            if (remoteCluster.type == ClusterNodeType.REMOTE)
                throw UnsupportedOperationException()
            replicaContext.executeType = TaskExecuteType.DELTA
            when (event.type) {
                EventType.METADATA_DELETED -> handleMetadataDeleted(replicaContext)
                EventType.METADATA_SAVED -> handleMetadataSaved(replicaContext)
                EventType.NODE_RENAMED -> handleNodeRenamed(replicaContext)
                EventType.NODE_COPIED -> handleNodeCopied(replicaContext)
                EventType.NODE_MOVED -> handleNodeMoved(replicaContext)
                EventType.NODE_DELETED -> handleNodeDeleted(replicaContext)
                EventType.NODE_CREATED -> handleNodeCreated(replicaContext)
                EventType.VERSION_CREATED -> handleVersionCreated(replicaContext)
                EventType.VERSION_UPDATED -> handleVersionUpdated(replicaContext)
                EventType.VERSION_DELETED -> handleVersionDeleted(replicaContext)
                else -> throw UnsupportedOperationException()
            }
        }
    }

    private fun handleMetadataDeleted(replicaContext: ReplicaContext) {
        with(replicaContext) {
            val metadataDeleteRequest = MetadataDeleteRequest(
                projectId = remoteProjectId!!,
                repoName = remoteRepoName!!,
                fullPath = event.resourceKey,
                keyList = (event.data["keys"] as? List<String>)?.toSet() ?: emptySet(),
                operator = event.userId
            )
            replicaByDeleteMetadata(replicaContext, metadataDeleteRequest)
        }
    }

    private fun handleMetadataSaved(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 兼容旧版本
            val nodeMetadata = parseMetadataModel(event)
            var metadata: Map<String, Any>? = null
            if (nodeMetadata == null) {
                metadata = event.data["metadata"] as? Map<String, Any>
            }
            val metadataSaveRequest = MetadataSaveRequest(
                projectId = remoteProjectId!!,
                repoName = remoteRepoName!!,
                fullPath = event.resourceKey,
                nodeMetadata = nodeMetadata,
                metadata = metadata,
                replace = event.data["replace"]?.toString()?.toBoolean() ?: false,
                operator = event.userId
            )
            replicaBySaveMetadata(replicaContext, metadataSaveRequest)
        }
    }


    private fun parseMetadataModel(event: ArtifactEvent): List<MetadataModel>? {
        return try {
            event.data["metadata"]?.toJsonString()?.readJsonString<Map<String, MetadataModel>>()?.values?.toList()
        } catch (e: Exception) {
            null
        }
    }

    private fun handleNodeRenamed(replicaContext: ReplicaContext) {
        with(replicaContext) {
            val nodeRenameRequest = NodeRenameRequest(
                projectId = remoteProjectId!!,
                repoName = remoteRepoName!!,
                fullPath = event.resourceKey,
                newFullPath = event.data["newFullPath"].toString(),
                operator = event.userId
            )
            replicaByRenamedNode(replicaContext, nodeRenameRequest)
        }
    }

    private fun handleNodeDeleted(replicaContext: ReplicaContext) {
        with(replicaContext) {
            val deleted = event.data["deletedDate"]?.toString()
            val pathConstraint = PathConstraint(event.resourceKey, deletedDate = deleted)
            replicaByDeletedNode(replicaContext, pathConstraint)
        }
    }

    private fun handleNodeCopied(replicaContext: ReplicaContext) {
        handleNodeMoveCopyEvent(replicaContext, moveOperation = false)
    }

    private fun handleNodeMoved(replicaContext: ReplicaContext) {
        handleNodeMoveCopyEvent(replicaContext, moveOperation = true)
    }

    private fun handleNodeCreated(replicaContext: ReplicaContext) {
        val pathConstraint = PathConstraint(replicaContext.event.resourceKey)
        replicaByPathConstraint(replicaContext, pathConstraint)
    }

    private fun handleVersionCreated(replicaContext: ReplicaContext) {
        val packageKey = replicaContext.event.data["packageKey"].toString()
        val packageVersion = replicaContext.event.data["packageVersion"].toString()
        val packageConstraint = PackageConstraint(packageKey, listOf(packageVersion))
        replicaByPackageConstraint(replicaContext, packageConstraint)
    }

    private fun handleVersionUpdated(replicaContext: ReplicaContext) {
        val packageKey = replicaContext.event.data["packageKey"].toString()
        val packageVersion = replicaContext.event.data["packageVersion"].toString()
        val packageConstraint = PackageConstraint(packageKey, listOf(packageVersion))
        replicaByPackageConstraint(replicaContext, packageConstraint)
    }

    private fun handleVersionDeleted(replicaContext: ReplicaContext) {
        val packageKey = replicaContext.event.data["packageKey"].toString()
        val packageName = replicaContext.event.data["packageName"].toString()
        val deleted = replicaContext.event.data["deletedDate"]?.toString()
        val packageVersion = replicaContext.event.data["packageVersion"]?.toString()
        if (deleted.isNullOrEmpty()) return

        val packageVersionDeleteSummary = PackageVersionDeleteSummary(
            projectId = replicaContext.localProjectId,
            repoName = replicaContext.localRepoName,
            packageName = packageName,
            packageKey = packageKey,
            versionName = packageVersion,
            deletedDate = deleted
        )
        replicaByDeletedPackage(replicaContext, packageVersionDeleteSummary)
    }

    /**
     * 处理节点移动或复制事件
     * @param replicaContext 复制上下文
     * @param moveOperation 是否为移动操作，true表示移动，false表示复制
     */
    private fun handleNodeMoveCopyEvent(replicaContext: ReplicaContext, moveOperation: Boolean) {
        with(replicaContext) {
            val dstProjectId = event.data["dstProjectId"].toString()
            val dstRepoName = event.data["dstRepoName"].toString()
            val dstFullPath = event.data["dstFullPath"].toString()
            val destNodeFolder = event.data["destNodeFolder"]?.toString()?.toBoolean()
            val overwrite = event.data["overwrite"]?.toString()?.toBoolean() ?: false
            // 检查是否为跨项目/跨仓库的操作
            if (crossProjectRepoOperation(replicaContext, dstProjectId, dstRepoName)) {
                handleCrossProjectMove(replicaContext, moveOperation)
                return
            }
            // 当目标节点为文件夹时，先确保目标集群上节点存在
            ensureTargetFolderExists(replicaContext, dstFullPath)
            val nodeCopyOrMoveRequest = NodeMoveCopyRequest(
                srcProjectId = remoteProjectId!!,
                srcRepoName = remoteRepoName!!,
                srcFullPath = event.resourceKey,
                destProjectId = remoteProjectId,
                destRepoName = remoteRepoName,
                destFullPath = dstFullPath,
                destNodeFolder = destNodeFolder,
                overwrite = overwrite,
                operator = event.userId
            )
            replicaByMovedOrCopiedNode(this, nodeCopyOrMoveRequest, moveOperation)
        }
    }

    private fun crossProjectRepoOperation(
        context: ReplicaContext,
        dstProjectId: String,
        dstRepoName: String
    ): Boolean {
        return dstProjectId != context.localProjectId || dstRepoName != context.localRepoName
    }

    private fun handleCrossProjectMove(context: ReplicaContext, moveOperation: Boolean) {
        if (!moveOperation) return

        localDataManager.findDeletedNodeDetail(
            context.localProjectId,
            context.localRepoName,
            context.event.resourceKey
        )?.let { deletedNode ->
            val pathConstraint = PathConstraint(
                context.event.resourceKey,
                deletedDate = deletedNode.nodeInfo.deleted
            )
            replicaByDeletedNode(context, pathConstraint)
        }
    }

    private fun ensureTargetFolderExists(context: ReplicaContext, dstFullPath: String) {
        localDataManager.findNode(
            context.localProjectId, context.localRepoName, dstFullPath
        )?.let { node ->
            if (node.folder) {
                try {
                    val pathConstraint = PathConstraint(dstFullPath)
                    replicaFolderOnlyByPathConstraint(context, pathConstraint)
                } catch (_: Exception) {
                }
            }
        }
    }
}