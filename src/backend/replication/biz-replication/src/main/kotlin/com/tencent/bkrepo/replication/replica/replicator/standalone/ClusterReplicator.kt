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
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.request.BlockNodeCreateFinishRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.ClusterBlockFileThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.replicator.base.AbstractFileReplicator
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.internal.PackageNodeMappings
import com.tencent.bkrepo.repository.pojo.blocknode.service.BlockNodeCreateRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
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
import java.util.concurrent.TimeUnit

/**
 * 集群数据同步类
 * 独立集群 同步到 独立集群 的同步实现类
 */
@Component
class ClusterReplicator(
    localDataManager: LocalDataManager,
    artifactReplicationHandler: ClusterArtifactReplicationHandler,
    replicationProperties: ReplicationProperties,
) : AbstractFileReplicator(artifactReplicationHandler, replicationProperties, localDataManager) {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    private val blockNodeExecutor = ClusterBlockFileThreadPoolExecutor.instance

    private val remoteRepoCache = CacheBuilder.newBuilder().maximumSize(50)
        .expireAfterWrite(60, TimeUnit.SECONDS).build<String, RepositoryDetail>()

    override fun checkVersion(context: ReplicaContext) {
        with(context) {
            val remoteVersion = artifactReplicaClient!!.version().data.orEmpty()
            if (version != remoteVersion) {
                logger.warn("Local cluster's version[$version] is different from remote cluster[$remoteVersion].")
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
                operator = localProject.createdBy
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
            remoteRepoCache.getIfPresent(key) ?: let {
                val request = RepoCreateRequest(
                    projectId = remoteProjectId,
                    name = remoteRepoName,
                    type = remoteRepoType,
                    category = localRepo.category,
                    public = localRepo.public,
                    description = localRepo.description,
                    configuration = localRepo.configuration,
                    operator = localRepo.createdBy
                )
                remoteRepoCache.put(key, artifactReplicaClient!!.replicaRepoCreateRequest(request).data!!)
            }
            context.remoteRepo = remoteRepoCache.getIfPresent(key)
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
            // 文件数据
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
            packageMetadata.add(MetadataModel(SOURCE_TYPE, ArtifactChannel.REPLICATION))
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
                createdBy = packageVersion.createdBy
            )
            artifactReplicaClient!!.replicaPackageVersionCreatedRequest(request)
        }
        return true
    }

    override fun replicaDeletedPackage(
        context: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary
    ): Boolean {
        return true
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        var fileReplicaStatus = true
        if (unNormalNode(node)) {
            fileReplicaStatus = handleBlockNodeReplication(context, node)
        }
        if (fileReplicaStatus) {
            fileReplicaStatus = handleNormalNodeReplication(context, node)
        }
        return fileReplicaStatus
    }

    private fun handleBlockNodeReplication(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            val blockNodeList = validateAndGetBlockNodeList(context, node) ?: return false
            if (blockNodeList.isEmpty()) return true
            val uploadId = "${StringPool.uniqueId()}/${node.id}"

            // 并发传输文件，每个文件传输完成后创建对应的blocknode
            val success = executeBlockFileTransfer(
                blockNodeExecutor = blockNodeExecutor,
                context = context,
                node = node,
                blockNodeList = blockNodeList,
                pushBlockFile = { ctx, blockNode ->
                    pushBlockFileToCluster(ctx, blockNode, uploadId)
                },
                handleError = { ctx, nodeInfo, throwable -> handleBlockFileTransferError(ctx, nodeInfo, throwable) }
            )

            // 所有文件传输完成并创建blocknode后，调用finish请求
            if (success) {
                artifactReplicaClient!!.replicaBlockNodeCreateFinishRequest(
                    BlockNodeCreateFinishRequest(
                        projectId = remoteProjectId!!,
                        repoName = remoteRepoName!!,
                        uploadId = uploadId,
                        fullPath = node.fullPath
                    )
                )
            }

            return success
        }
    }

    /**
     * 推送块文件到集群
     */
    private fun pushBlockFileToCluster(context: ReplicaContext, blockNode: TBlockNode, uploadId: String) {
        executeFilePush(
            context = context,
            node = blockNode,
            logPrefix = "[Cluster-Block] ",
            afterCompletion = { ctx, _ ->
                logger.info(
                    "The block node ${blockNode.id} of [${blockNode.nodeFullPath}] " +
                        "will be pushed to the remote server!"
                )
                // 文件传输完成后，创建该blocknode的元数据
                buildBlockNodeCreateRequest(ctx, blockNode, uploadId)?.let { blockNodeCreateRequest ->
                    ctx.artifactReplicaClient!!.replicaBlockNodeCreateRequest(blockNodeCreateRequest)
                }
            }
        )
    }

    /**
     * 处理块文件传输错误
     */
    private fun handleBlockFileTransferError(context: ReplicaContext, node: NodeInfo, throwable: Throwable) {
        logger.warn(
            "replica block file of ${node.fullPath} with sha256 ${node.sha256} in repo " +
                "${node.projectId}|${node.repoName} failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
    }

    private fun handleNormalNodeReplication(context: ReplicaContext, node: NodeInfo): Boolean {
        val nodeCreateRequest = buildNodeCreateRequest(context, node) ?: return false
        executeNormalNodePush(context, node, nodeCreateRequest)
        return true
    }

    private fun executeNormalNodePush(
        context: ReplicaContext,
        node: NodeInfo,
        nodeCreateRequest: NodeCreateRequest
    ) {
        if (unNormalNode(node)) {
            context.artifactReplicaClient!!.replicaNodeCreateRequest(nodeCreateRequest)
            return
        }
        executeFilePush(
            context = context,
            node = node,
            logPrefix = "[Cluster] ",
            postPush = { ctx, _ ->
                // 再次确认下文件是否已经可见(cfs可见性问题)
                doubleCheck(ctx, node.sha256!!)
            },
            afterCompletion = { ctx, _ ->
                logger.info("The node [${node.fullPath}] will be pushed to the remote server!")
                // 同步节点信息
                context.artifactReplicaClient!!.replicaNodeCreateRequest(nodeCreateRequest)
            }
        )
    }

    private fun doubleCheck(context: ReplicaContext, sha256: String) {
        if (!context.task.setting.storageConsistencyCheck) return
        logger.info("will check the storage consistency for $sha256")
        var checkResult = false
        var costTime = 0
        val remoteRepositoryType = context.remoteRepoType
        while (!checkResult && costTime < STORAGE_CONSISTENCY_CHECK_TIME_OUT) {
            if (context.blobReplicaClient!!.check(
                    sha256 = sha256,
                    storageKey = context.remoteRepo?.storageCredentials?.key,
                    repoType = remoteRepositoryType
                ).data == true
            ) {
                checkResult = true
            } else {
                TimeUnit.SECONDS.sleep(1)
                costTime++
            }
            logger.info(
                "the result of storage consistency check for $sha256 is $checkResult," +
                    " costTime: $costTime seconds"
            )
        }
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            buildNodeCreateRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeCreateRequest(it)
            }
        }
    }

    override fun replicaDeletedNode(context: ReplicaContext, node: NodeInfo): Boolean {
        return true
    }

    override fun replicaNodeMove(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        return true
    }

    override fun replicaNodeCopy(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        return true
    }

    override fun replicaNodeRename(context: ReplicaContext, nodeRenameRequest: NodeRenameRequest): Boolean {
        return true
    }

    override fun replicaMetadataSave(context: ReplicaContext, metadataSaveRequest: MetadataSaveRequest): Boolean {
        return true
    }

    override fun replicaMetadataDelete(context: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest): Boolean {
        return true
    }

    private fun buildNodeCreateRequest(context: ReplicaContext, node: NodeInfo): NodeCreateRequest? {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            // 查询元数据
            val metadata = if (task.setting.includeMetadata) node.nodeMetadata else emptyList()
            return NodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                folder = node.folder,
                overwrite = true,
                size = node.size,
                sha256 = node.sha256!!,
                md5 = node.md5!!,
                crc64ecma = node.crc64ecma,
                nodeMetadata = metadata,
                operator = node.createdBy,
                createdBy = node.createdBy,
                createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(node.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    private fun buildBlockNodeCreateRequest(
        context: ReplicaContext,
        blockNode: TBlockNode,
        uploadId: String
    ): BlockNodeCreateRequest? {
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
                uploadId = uploadId,
                deleted = blockNode.deleted
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterReplicator::class.java)
        private const val STORAGE_CONSISTENCY_CHECK_TIME_OUT = 600

        fun buildRemoteRepoCacheKey(clusterInfo: ClusterInfo, projectId: String, repoName: String): String {
            return "$projectId/$repoName/${clusterInfo.hashCode()}"
        }
    }
}