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

package com.tencent.bkrepo.replication.replica.replicator.standalone

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.constant.FEDERATED
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.FederationFileThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.replicator.base.AbstractFileReplicator
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.internal.PackageNodeMappings
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.repository.pojo.blocknode.service.BlockNodeCreateRequest
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.DeletedNodeReplicationRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 联邦仓库数据同步类
 */
@Component
class FederationReplicator(
    private val localDataManager: LocalDataManager,
    artifactReplicationHandler: ClusterArtifactReplicationHandler,
    replicationProperties: ReplicationProperties,
    private val federationRepositoryService: FederationRepositoryService,
    private val replicaRecordService: ReplicaRecordService,
) : AbstractFileReplicator(artifactReplicationHandler, replicationProperties) {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    private val remoteRepoCache = ConcurrentHashMap<String, RepositoryDetail>()

    private val executor = FederationFileThreadPoolExecutor.instance

    override fun checkVersion(context: ReplicaContext) {
        with(context) {
            val remoteVersion = artifactReplicaClient!!.version().data.orEmpty()
            if (version != remoteVersion) {
                logger.warn("Local cluster's version[$version] is different from federated cluster[$remoteVersion].")
            }
        }
    }

    override fun replicaProject(context: ReplicaContext) {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank()) return
            val localProject = localDataManager.findProjectById(localProjectId)
            val request = ProjectCreateRequest(
                name = remoteProjectId,
                displayName = remoteProjectId,
                description = localProject.description,
                operator = localProject.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
            artifactReplicaClient!!.replicaProjectCreateRequest(request)
        }
    }

    override fun replicaRepo(context: ReplicaContext) {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return
            val localRepo = localDataManager.findRepoByName(localProjectId, localRepoName, localRepoType.name)
            val key = buildRemoteRepoCacheKey(cluster, remoteProjectId, remoteRepoName)
            context.remoteRepo = remoteRepoCache.getOrPut(key) {
                val request = RepoCreateRequest(
                    projectId = remoteProjectId,
                    name = remoteRepoName,
                    type = remoteRepoType,
                    category = localRepo.category,
                    public = localRepo.public,
                    description = localRepo.description,
                    configuration = localRepo.configuration,
                    operator = localRepo.createdBy,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name)
                )
                val createdRemoteRepo = artifactReplicaClient!!.replicaRepoCreateRequest(request).data!!
                // 目标节点已有名称相同但类型不同的仓库时抛出异常
                if (createdRemoteRepo.type != remoteRepoType) {
                    throw ErrorCodeException(
                        ArtifactMessageCode.REPOSITORY_EXISTED,
                        "$remoteProjectId/$remoteRepoName",
                        status = HttpStatus.CONFLICT
                    )
                }
                createdRemoteRepo
            }
        }
    }

    override fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary) {
        // do nothing
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
    ): Boolean {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return true
            PackageNodeMappings.map(
                packageSummary = packageSummary,
                packageVersion = packageVersion,
                type = localRepoType
            ).forEach {
                val node = try {
                    localDataManager.findNodeDetailInVersion(
                        projectId = localProjectId,
                        repoName = localRepoName,
                        fullPath = it
                    )
                } catch (e: NodeNotFoundException) {
                    logger.warn("Node $it not found in repo $localProjectId|$localRepoName")
                    throw e
                }
                replicaFile(context, node.nodeInfo)
            }
            val packageMetadata = packageVersion.packageMetadata as MutableList<MetadataModel>
            packageMetadata.add(MetadataModel(FEDERATED, false, true))
            // 包数据
            val request = PackageVersionCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                packageName = packageSummary.name,
                packageKey = packageSummary.key,
                packageType = packageSummary.type,
                packageDescription = packageSummary.description,
                versionName = packageVersion.name,
                size = packageVersion.size,
                manifestPath = packageVersion.manifestPath,
                artifactPath = packageVersion.contentPath,
                stageTag = packageVersion.stageTag,
                packageMetadata = packageMetadata,
                extension = packageVersion.extension,
                overwrite = true,
                createdBy = packageVersion.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
            artifactReplicaClient!!.replicaPackageVersionCreatedRequest(request)
        }
        return true
    }

    override fun replicaDeletedPackage(
        context: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary,
    ): Boolean {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return false
            if (packageVersionDeleteSummary.versionName.isNullOrEmpty()) {
                // 构建包删除请求
                val packageDeleteRequest = PackageDeleteRequest(
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    packageKey = packageVersionDeleteSummary.packageKey,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                    deletedDate = packageVersionDeleteSummary.deletedDate
                )
                artifactReplicaClient!!.replicaPackageDeleteRequest(packageDeleteRequest)
            } else {
                // 构建包版本删除请求
                val versionDeleteRequest = PackageVersionDeleteRequest(
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    packageKey = packageVersionDeleteSummary.packageKey,
                    versionName = packageVersionDeleteSummary.versionName!!,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                    deletedDate = packageVersionDeleteSummary.deletedDate
                )
                artifactReplicaClient!!.replicaPackageVersionDeleteRequest(versionDeleteRequest)
            }
        }
        return true
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            //  同步block node
            if (unNormalNode(node)) {
                return replicaBlockNode(context, node)
            }

            // 同步Node节点
            if (!syncNodeToFederatedCluster(this, node)) return false

            //  同步文件
            return replicaNormalFile(context, node)
        }
    }

    /**
     * 复制块节点文件
     */
    private fun replicaBlockNode(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            if (!blockNode(node)) {
                logger.warn("Node ${node.fullPath} in repo ${node.projectId}|${node.repoName} is link node.")
                return false
            }

            val blockNodeList = localDataManager.listBlockNode(node)
            if (blockNodeList.isEmpty()) {
                logger.warn("Block node of ${node.fullPath} in repo ${node.projectId}|${node.repoName} is empty.")
            }

            // 传输所有blocknode元数据
            blockNodeList.forEach { blockNode ->
                buildBlockNodeCreateRequest(this, blockNode)?.let { blockNodeCreateRequest ->
                    context.artifactReplicaClient!!.replicaBlockNodeCreateRequest(blockNodeCreateRequest)
                }
            }

            // 同步节点
            if (!syncNodeToFederatedCluster(this, node)) return false

            // 并发传输文件
            val success = executeBlockFileTransfer(context, node, blockNodeList)
            if (!success) return false

            // 保存元数据标识传输完成
            saveNodeMetadata(context, node)
            return true
        }
    }

    /**
     * 复制普通文件
     */
    private fun replicaNormalFile(context: ReplicaContext, node: NodeInfo): Boolean {
        return if (executor.activeCount < replicationProperties.federatedFileConcurrencyNum) {
            // 异步执行
            executeFileTransferAsync(context, node)
        } else {
            // 同步执行
            executeFileTransferSync(context, node)
        }
    }

    /**
     * 执行块文件传输
     */
    private fun executeBlockFileTransfer(
        context: ReplicaContext,
        node: NodeInfo,
        blockNodeList: List<TBlockNode>
    ): Boolean {
        val latch = CountDownLatch(blockNodeList.size)
        val failureCount = AtomicInteger(0)

        blockNodeList.forEach { blockNode ->
            if (executor.activeCount < replicationProperties.federatedFileConcurrencyNum) {
                // 异步执行
                executor.execute(
                    Runnable {
                        try {
                            pushBlockFileToFederatedCluster(context, blockNode)
                        } catch (throwable: Throwable) {
                            handleBlockFileTransferError(context, node, throwable)
                            failureCount.incrementAndGet()
                        } finally {
                            latch.countDown()
                        }
                    }.trace()
                )
            } else {
                // 同步执行
                try {
                    pushBlockFileToFederatedCluster(context, blockNode)
                } catch (throwable: Throwable) {
                    handleBlockFileTransferError(context, node, throwable)
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等待所有文件传输完成
        latch.await()
        return failureCount.get() == 0
    }

    /**
     * 异步执行文件传输
     */
    private fun executeFileTransferAsync(context: ReplicaContext, node: NodeInfo): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicBoolean(true)

        executor.execute(
            Runnable {
                try {
                    pushFileToFederatedCluster(context, node)
                } catch (throwable: Throwable) {
                    handleFileTransferError(context, node, throwable)
                    result.set(false)
                } finally {
                    latch.countDown()
                }
            }.trace()
        )

        latch.await()
        return result.get()
    }

    /**
     * 同步执行文件传输
     */
    private fun executeFileTransferSync(context: ReplicaContext, node: NodeInfo): Boolean {
        return try {
            pushFileToFederatedCluster(context, node)
            true
        } catch (throwable: Throwable) {
            handleFileTransferError(context, node, throwable)
            false
        }
    }

    /**
     * 处理块文件传输错误
     */
    private fun handleBlockFileTransferError(context: ReplicaContext, node: NodeInfo, throwable: Throwable) {
        logger.error(
            "replica block file of ${node.fullPath} with sha256 ${node.sha256} in repo " +
                "${node.projectId}|${node.repoName} failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
        completeFileReplicaRecord(context, false)
    }

    /**
     * 处理文件传输错误
     */
    private fun handleFileTransferError(context: ReplicaContext, node: NodeInfo, throwable: Throwable) {
        logger.error(
            "replica file ${node.fullPath} with sha256 ${node.sha256} in repo " +
                "${node.projectId}|${node.repoName} failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
        completeFileReplicaRecord(context, false)
    }

    /**
     * 保存节点元数据
     */
    private fun saveNodeMetadata(context: ReplicaContext, node: NodeInfo) {
        if (node.deleted != null) {
            context.artifactReplicaClient!!.replicaMetadataSaveRequestForDeletedNode(
                buildDeletedNodeMetadataSaveRequest(context, node, context.task.name)
            )
        } else {
            context.artifactReplicaClient!!.replicaMetadataSaveRequest(
                buildMetadataSaveRequest(context, node, context.task.name)
            )
        }
    }

    private fun completeFileReplicaRecord(context: ReplicaContext, success: Boolean = true) {
        with(context) {
            if (task.record == false || recordDetailId.isNullOrEmpty()) return
            replicaRecordService.updateRecordDetailProgress(recordDetailId!!, success)
        }
    }

    private fun pushBlockFileToFederatedCluster(context: ReplicaContext, blockNode: TBlockNode) {
        executeFilePush(
            context = context,
            node = blockNode,
            logPrefix = "[Federation-Block] ",
            afterCompletion = { ctx, _ ->
                completeFileReplicaRecord(ctx)
            }
        )
    }

    /**
     * 推送文件到联邦集群
     */
    private fun pushFileToFederatedCluster(context: ReplicaContext, node: NodeInfo) {
        executeFilePush(
            context = context,
            node = node,
            logPrefix = "[Federation] ",
            afterCompletion = { ctx, nodeInfo ->
                // 通过文件传输完成标识
                if (nodeInfo.deleted != null) {
                    ctx.artifactReplicaClient!!.replicaMetadataSaveRequestForDeletedNode(
                        buildDeletedNodeMetadataSaveRequest(ctx, nodeInfo, ctx.task.name)
                    )
                } else {
                    ctx.artifactReplicaClient!!.replicaMetadataSaveRequest(
                        buildMetadataSaveRequest(ctx, nodeInfo, ctx.task.name)
                    )
                }
            }
        )
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            buildNodeCreateRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeCreateRequest(it)
            }
        }
    }

    override fun replicaDeletedNode(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            buildNodeDeleteRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeDeleteRequest(it)
                return true
            }
            return false
        }
    }

    override fun replicaNodeMove(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        with(context) {
            buildNodeMoveCopyRequest(this, moveOrCopyRequest).let {
                artifactReplicaClient!!.replicaNodeMoveRequest(it)
            }
            return true
        }
    }

    override fun replicaNodeCopy(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        with(context) {
            buildNodeMoveCopyRequest(this, moveOrCopyRequest).let {
                artifactReplicaClient!!.replicaNodeCopyRequest(it)
            }
            return true
        }
    }

    override fun replicaNodeRename(context: ReplicaContext, nodeRenameRequest: NodeRenameRequest): Boolean {
        with(context) {
            buildNodeRenameRequest(this, nodeRenameRequest).let {
                artifactReplicaClient!!.replicaNodeRenameRequest(it)
            }
            return true
        }
    }

    override fun replicaMetadataSave(context: ReplicaContext, metadataSaveRequest: MetadataSaveRequest): Boolean {
        with(context) {
            buildMetadataSaveRequest(this, metadataSaveRequest).let {
                artifactReplicaClient!!.replicaMetadataSaveRequest(it)
            }
            return true
        }
    }

    override fun replicaMetadataDelete(context: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest): Boolean {
        with(context) {
            buildMetadataDeleteRequest(this, metadataDeleteRequest).let {
                artifactReplicaClient!!.replicaMetadataDeleteRequest(it)
            }
            return true
        }
    }

    private fun getCurrentClusterName(projectId: String, repoName: String, taskName: String): String {
        val key = parseKeyFromTaskName(taskName)
        return federationRepositoryService.getCurrentClusterName(projectId, repoName, key)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, "self")
    }

    private fun parseKeyFromTaskName(taskName: String): String {
        val parts = taskName.split("/")
        require(parts.size >= 3) { "Invalid task name format" }
        return parts[1]
    }

    private fun buildNodeMoveCopyRequest(
        context: ReplicaContext,
        moveOrCopyRequest: NodeMoveCopyRequest
    ): NodeMoveCopyRequest {
        with(moveOrCopyRequest) {
            return moveOrCopyRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildNodeRenameRequest(
        context: ReplicaContext,
        nodeRenameRequest: NodeRenameRequest
    ): NodeRenameRequest {
        with(nodeRenameRequest) {
            return nodeRenameRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildMetadataSaveRequest(
        context: ReplicaContext,
        metadataSaveRequest: MetadataSaveRequest
    ): MetadataSaveRequest {
        with(metadataSaveRequest) {
            return metadataSaveRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildMetadataDeleteRequest(
        context: ReplicaContext,
        metadataDeleteRequest: MetadataDeleteRequest
    ): MetadataDeleteRequest {
        with(metadataDeleteRequest) {
            return metadataDeleteRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildNodeDeleteRequest(context: ReplicaContext, node: NodeInfo): NodeDeleteRequest? {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            return NodeDeleteRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                operator = node.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                deletedDate = node.deleted
            )
        }
    }


    private fun buildDeletedNodeReplicaRequest(
        context: ReplicaContext, node: NodeInfo,
    ): DeletedNodeReplicationRequest? {
        return buildNodeCreateRequest(context, node)?.let { baseRequest ->
            DeletedNodeReplicationRequest(
                nodeCreateRequest = baseRequest,
                deleted = LocalDateTime.parse(node.deleted, DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    private fun syncNodeToFederatedCluster(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            val request = if (node.deleted != null) {
                buildDeletedNodeReplicaRequest(this, node)?.also {
                    logger.info("The deleted node [${node.fullPath}] will be pushed to the federated cluster server!")
                }
            } else {
                buildNodeCreateRequest(this, node)?.also {
                    logger.info("The node [${node.fullPath}] will be pushed to the federated cluster server!")
                }
            } ?: return false

            if (node.deleted != null) {
                artifactReplicaClient!!.replicaDeletedNodeReplicationRequest(request as DeletedNodeReplicationRequest)
            } else {
                artifactReplicaClient!!.replicaNodeCreateRequest(request as NodeCreateRequest)
            }
            return true
        }
    }

    private fun buildNodeCreateRequest(context: ReplicaContext, node: NodeInfo): NodeCreateRequest? {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            val metadata = if (task.setting.includeMetadata) {
                node.nodeMetadata ?: emptyList()
            } else {
                emptyList()
            }
            val updatedMetadata = metadata.plus(MetadataModel(FEDERATED, false, true))
            return NodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                folder = node.folder,
                overwrite = if (node.folder) false else true,
                size = if (node.folder) null else node.size,
                sha256 = if (node.folder) null else node.sha256!!,
                md5 = if (node.folder) null else node.md5!!,
                crc64ecma = if (node.folder) null else node.crc64ecma,
                nodeMetadata = if (node.folder) emptyList() else updatedMetadata,
                operator = node.createdBy,
                createdBy = node.createdBy,
                createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(node.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME),
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
        }
    }

    private fun buildBlockNodeCreateRequest(context: ReplicaContext, blockNode: TBlockNode): BlockNodeCreateRequest? {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            return BlockNodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = blockNode.nodeFullPath,
                expireDate = blockNode.expireDate,
                size = blockNode.size,
                sha256 = blockNode.sha256,
                crc64ecma = blockNode.crc64ecma,
                startPos = blockNode.startPos,
                endPos = blockNode.endPos,
                createdBy = blockNode.createdBy,
                createdDate = blockNode.createdDate,
                uploadId = blockNode.uploadId
            )
        }
    }

    private fun buildDeletedNodeMetadataSaveRequest(
        context: ReplicaContext,
        node: NodeInfo,
        taskName: String,
    ): DeletedNodeMetadataSaveRequest {
        return DeletedNodeMetadataSaveRequest(
            metadataSaveRequest = buildMetadataSaveRequest(context, node, taskName),
            deleted = LocalDateTime.parse(node.deleted, DateTimeFormatter.ISO_DATE_TIME)
        )
    }

    private fun buildMetadataSaveRequest(
        context: ReplicaContext,
        node: NodeInfo,
        taskName: String,
    ): MetadataSaveRequest {
        return MetadataSaveRequest(
            projectId = context.remoteProjectId!!,
            repoName = context.remoteRepoName!!,
            fullPath = node.fullPath,
            nodeMetadata = listOf(MetadataModel(FEDERATED, true, true)),
            operator = node.createdBy,
            source = getCurrentClusterName(node.projectId, node.repoName, taskName)
        )
    }

    companion object {

        private val logger = LoggerFactory.getLogger(FederationReplicator::class.java)

        fun buildRemoteRepoCacheKey(clusterInfo: ClusterInfo, projectId: String, repoName: String): String {
            return "$projectId/$repoName/${clusterInfo.hashCode()}"
        }
    }
}