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
import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.FEDERATED
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.Replicator
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.internal.PackageNodeMappings
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.DeletedNodeReplicationRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
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

/**
 * 联邦仓库数据同步类
 */
@Component
class FederationReplicator(
    private val localDataManager: LocalDataManager,
    private val artifactReplicationHandler: ClusterArtifactReplicationHandler,
    private val replicationProperties: ReplicationProperties,
    private val federationRepositoryService: FederationRepositoryService,
) : Replicator {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    private val remoteRepoCache = ConcurrentHashMap<String, RepositoryDetail>()

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
                artifactReplicaClient!!.replicaRepoCreateRequest(request).data!!
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

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            var type: String = replicationProperties.pushType
            var downGrade = false
            val remoteRepositoryType = context.remoteRepoType
            // 1. 同步节点
            if (!syncNodeToFederatedCluster(this, node)) return false

            // TODO node和文件分发拆分成并发执行
            // 2. 同步文件元数据
            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retry ->
                if (blobReplicaClient!!.check(
                        node.sha256!!,
                        remoteRepo?.storageCredentials?.key,
                        remoteRepositoryType
                    ).data != true
                ) {
                    // 1. 同步文件数据
                    logger.info(
                        "The file [${node.fullPath}] with sha256 [${node.sha256}] " +
                            "will be pushed to the federated server ${cluster.name}, try the $retry time!"
                    )
                    try {
                        artifactReplicationHandler.blobPush(
                            filePushContext = FilePushContext(
                                context = context,
                                name = node.fullPath,
                                size = node.size,
                                sha256 = node.sha256,
                                md5 = node.md5
                            ),
                            pushType = type,
                            downGrade = downGrade
                        )
                    } catch (throwable: Throwable) {
                        logger.warn(
                            "File replica push error $throwable, trace is " +
                                "${Throwables.getStackTraceAsString(throwable)}!"
                        )
                        // 当不支持分块上传时，降级为普通上传
                        // 兼容接口不存在时，会返回401
                        if (
                            throwable is ArtifactPushException &&
                            (
                                throwable.code == HttpStatus.METHOD_NOT_ALLOWED.value ||
                                    throwable.code == HttpStatus.UNAUTHORIZED.value
                                )
                        ) {
                            type = WayOfPushArtifact.PUSH_WITH_DEFAULT.value
                            downGrade = true
                        }
                        throw throwable
                    }
                }

                // 3. 通过文件传输完成标识
                artifactReplicaClient!!.replicaMetadataSaveRequest(
                    buildMetadataSaveRequest(context, node, task.name)
                )
                return true
            }
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
        with(context) {
            buildNodeDeleteRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeDeleteRequest(it)
                return true
            }
            return false
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

    private fun buildNodeCreateRequest(context: ReplicaContext, node: NodeInfo): NodeCreateRequest? {
        return buildBaseRequest(context, node)?.let { baseRequest ->
            NodeCreateRequest(
                projectId = baseRequest.projectId,
                repoName = baseRequest.repoName,
                fullPath = baseRequest.fullPath,
                folder = baseRequest.folder,
                overwrite = baseRequest.overwrite,
                size = baseRequest.size,
                sha256 = baseRequest.sha256,
                md5 = baseRequest.md5,
                nodeMetadata = baseRequest.nodeMetadata,
                operator = baseRequest.operator,
                createdBy = baseRequest.createdBy,
                createdDate = baseRequest.createdDate,
                lastModifiedBy = baseRequest.lastModifiedBy,
                lastModifiedDate = baseRequest.lastModifiedDate,
                source = baseRequest.source
            )
        }
    }

    private fun buildDeletedNodeReplicaRequest(
        context: ReplicaContext, node: NodeInfo,
    ): DeletedNodeReplicationRequest? {
        return buildBaseRequest(context, node)?.let { baseRequest ->
            DeletedNodeReplicationRequest(
                projectId = baseRequest.projectId,
                repoName = baseRequest.repoName,
                fullPath = baseRequest.fullPath,
                folder = baseRequest.folder,
                overwrite = baseRequest.overwrite,
                size = baseRequest.size,
                sha256 = baseRequest.sha256,
                md5 = baseRequest.md5,
                nodeMetadata = baseRequest.nodeMetadata,
                operator = baseRequest.operator,
                createdBy = baseRequest.createdBy,
                createdDate = baseRequest.createdDate,
                lastModifiedBy = baseRequest.lastModifiedBy,
                lastModifiedDate = baseRequest.lastModifiedDate,
                source = baseRequest.source,
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

    private data class BaseRequest(
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val folder: Boolean,
        val overwrite: Boolean,
        val size: Long?,
        val sha256: String,
        val md5: String,
        val nodeMetadata: List<MetadataModel>,
        val operator: String,
        val createdBy: String,
        val createdDate: LocalDateTime,
        val lastModifiedBy: String,
        val lastModifiedDate: LocalDateTime,
        val source: String,
    )

    private fun buildBaseRequest(context: ReplicaContext, node: NodeInfo): BaseRequest? {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            val metadata = if (task.setting.includeMetadata) {
                node.nodeMetadata ?: emptyList()
            } else {
                emptyList()
            }
            val updatedMetadata = metadata.plus(MetadataModel(FEDERATED, false, true))
            return BaseRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                folder = node.folder,
                overwrite = true,
                size = node.size,
                sha256 = node.sha256!!,
                md5 = node.md5!!,
                nodeMetadata = updatedMetadata,
                operator = node.createdBy,
                createdBy = node.createdBy,
                createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(node.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME),
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
        }
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
