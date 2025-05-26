/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.artifact

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.constant.CUSTOM
import com.tencent.bkrepo.auth.constant.PIPELINE
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_RANGE
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.ROOT
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.PARAM_PREVIEW
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_MD5
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.chunked.ChunkedUploadUtils
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.PipelineNodeService
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_BUILD_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_FOLDER_BUILD_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_OVERWRITE_COUNT
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_PIPELINE_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_PROJECT_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_SUB_BUILD_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_SUB_PIPELINE_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_SUB_PROJECT_ID
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_UPLOAD_CHANNEL
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager.Companion.METADATA_USER_ID
import com.tencent.bkrepo.common.security.manager.ci.PipelineBuildStatus
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.generic.artifact.context.GenericArtifactSearchContext
import com.tencent.bkrepo.generic.constant.BKREPO_META
import com.tencent.bkrepo.generic.constant.BKREPO_META_PREFIX
import com.tencent.bkrepo.generic.constant.CHUNKED_UPLOAD
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_BLOCK_APPEND
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_MD5
import com.tencent.bkrepo.generic.constant.HEADER_OFFSET
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.constant.HEADER_SEQUENCE
import com.tencent.bkrepo.generic.constant.HEADER_SHA256
import com.tencent.bkrepo.generic.constant.HEADER_SIZE
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_TYPE
import com.tencent.bkrepo.generic.constant.SEPARATE_UPLOAD
import com.tencent.bkrepo.generic.pojo.ChunkedResponseProperty
import com.tencent.bkrepo.generic.pojo.SeparateBlockInfo
import com.tencent.bkrepo.generic.util.ChunkedRequestUtil.uploadResponse
import com.tencent.bkrepo.replication.api.ClusterNodeClient
import com.tencent.bkrepo.replication.api.ReplicaTaskClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.repository.constant.NODE_DETAIL_LIST_KEY
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.common.metadata.pojo.node.NodeListOption
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.common.metadata.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import java.net.URLDecoder
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import kotlin.reflect.full.memberProperties

@Component
class GenericLocalRepository(
    private val replicaTaskClient: ReplicaTaskClient,
    private val clusterNodeClient: ClusterNodeClient,
    private val pipelineNodeService: PipelineNodeService,
    private val ciPermissionManager: CIPermissionManager,
    private val blockNodeService: BlockNodeService,
    private val storageProperties: StorageProperties,
) : LocalRepository() {

    private val edgeClusterNodeCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(1)
        .build<String, List<ClusterNodeInfo>>(
            CacheLoader.from { _ -> clusterNodeClient.listEdgeNodes().data.orEmpty() }
        )

    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        // 若不允许覆盖, 提前检查节点是否存在
        checkNodeExist(context)
        // 检查是否是覆盖流水线构件
        checkIfOverwritePipelineArtifact(context)
        // 校验sha256
        val calculatedSha256 = context.getArtifactSha256()
        val uploadSha256 = HeaderUtils.getHeader(HEADER_SHA256)
        if (uploadSha256 != null && !calculatedSha256.equals(uploadSha256, true)) {
            throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "sha256")
        }
        // 校验md5
        val calculatedMd5 = context.getArtifactMd5()
        val uploadMd5 = HeaderUtils.getHeader(HEADER_MD5)
        if (uploadMd5 != null && !calculatedMd5.equals(uploadMd5, true)) {
            throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "md5")
        }
        // 二次检查，防止接收文件过程中，有并发上传成功的情况
        checkNodeExist(context)
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val uploadId = context.request.getHeader(HEADER_UPLOAD_ID)
        val sequence = context.request.getHeader(HEADER_SEQUENCE)?.toInt()
        val uploadType = HeaderUtils.getHeader(HEADER_UPLOAD_TYPE)

        when {
            isSeparateUpload(uploadType) -> {
                if (uploadId.isNullOrEmpty()) {
                    throw ErrorCodeException(GenericMessageCode.BLOCK_UPLOADID_ERROR, uploadId)
                }
                onSeparateUpload(context, uploadId)
            }
            isBlockUpload(uploadId, sequence) -> {
                this.blockUpload(uploadId, sequence!!, context)
                context.response.contentType = MediaTypes.APPLICATION_JSON
                context.response.writer.println(ResponseBuilder.success().toJsonString())
            }
            isChunkedUpload(uploadType) -> {
                chunkedUpload(context)
            }
            else -> {
                val nodeDetail = storageManager.storeArtifactFile(
                    buildNodeCreateRequest(context),
                    context.getArtifactFile(),
                    context.storageCredentials
                )
                context.response.contentType = MediaTypes.APPLICATION_JSON
                context.response.addHeader(X_CHECKSUM_MD5, context.getArtifactMd5())
                context.response.addHeader(X_CHECKSUM_SHA256, context.getArtifactSha256())
                context.response.writer.println(ResponseBuilder.success(nodeDetail).toJsonString())
            }
        }
    }

    private fun onSeparateUpload(context: ArtifactUploadContext, uploadId: String) {
        with(context) {

            val blockArtifactFile = getArtifactFile()
            val sha256 = getArtifactSha256()

            val offset = context.request.getHeader(HEADER_OFFSET)?.toLongOrNull()
            val expires = storageProperties.receive.blockExpireTime

            val blockNode = TBlockNode(
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                nodeFullPath = artifactInfo.getArtifactFullPath(),
                startPos = offset ?: throw ErrorCodeException(GenericMessageCode.BLOCK_HEAD_NOT_FOUND),
                sha256 = sha256,
                projectId = projectId,
                repoName = repoName,
                size = blockArtifactFile.getSize(),
                uploadId = uploadId,
                expireDate = calculateExpiryDateTime(expires)
            )

            storageService.store(sha256, blockArtifactFile, storageCredentials)

            val blockNodeInfo = blockNodeService.createBlock(blockNode, storageCredentials)

            // Set response content type and write success response
            context.response.contentType = MediaTypes.APPLICATION_JSON
            context.response.writer.println(
                ResponseBuilder.success(
                    SeparateBlockInfo(
                        blockNodeInfo.size,
                        blockNodeInfo.sha256,
                        blockNodeInfo.startPos,
                        blockNodeInfo.uploadId
                    )
                ).toJsonString()
            )
        }
    }

    private fun isSeparateUpload(uploadType: String?): Boolean {
        return !uploadType.isNullOrEmpty() && uploadType == SEPARATE_UPLOAD
    }

    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        if (HttpContextHolder.getRequestOrNull()?.getParameter(PARAM_REPLICATE).toBoolean()) {
            val remoteClusterIds = edgeClusterNodeCache.get("").map { it.id!! }.toSet()
            if (remoteClusterIds.isEmpty()) {
                return
            }
            // TODO 如果分块上传也进行分发，此处需要修改
            replicaTaskClient.create(
                ReplicaTaskCreateRequest(
                    name = context.artifactInfo.getArtifactFullPath() +
                        "-${context.getArtifactSha256()}-${UUID.randomUUID()}",
                    localProjectId = context.projectId,
                    replicaObjectType = ReplicaObjectType.PATH,
                    replicaTaskObjects = listOf(
                        ReplicaObjectInfo(
                            localRepoName = context.repoName,
                            remoteProjectId = context.projectId,
                            remoteRepoName = context.repoName,
                            repoType = RepositoryType.GENERIC,
                            packageConstraints = null,
                            pathConstraints = listOf(PathConstraint(context.artifactInfo.getArtifactFullPath()))
                        )
                    ),
                    replicaType = ReplicaType.EDGE_PULL,
                    setting = ReplicaSetting(conflictStrategy = ConflictStrategy.OVERWRITE),
                    remoteClusterIds = emptySet()
                )
            )
        }
    }

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        // 文件默认下载，设置Content-Dispostition响应头
        // preview == true时不设置Content-Dispostition响应头
        val preview = context.request.getParameter(PARAM_PREVIEW)?.toBoolean()
        context.useDisposition = preview == null || preview == false
        if (context.repo.name == REPORT) {
            context.useDisposition = false
        }
    }

    /**
     * 支持单文件、目录、批量文件下载
     * 目录下载会以zip包形式将目录下的文件打包下载
     * 批量文件下载会以zip包形式将文件打包下载
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return if (context.artifacts.isNullOrEmpty()) {
            downloadSingleNode(context)
        } else {
            downloadMultiNode(context)
        }
    }

    private fun checkNodeExist(context: ArtifactUploadContext) {
        val overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE)
        val uploadId = HeaderUtils.getHeader(HEADER_UPLOAD_ID)
        val sequence = HeaderUtils.getHeader(HEADER_SEQUENCE)?.toInt()
        val uploadType = HeaderUtils.getHeader(HEADER_UPLOAD_TYPE)
        if (!overwrite && !isBlockUpload(uploadId, sequence)
            && !isChunkedUpload(uploadType) && !isSeparateUpload(uploadType)) {
            with(context.artifactInfo) {
                nodeService.getNodeDetail(this)?.let {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, getArtifactName())
                }
            }
        }
    }

    private fun checkIfOverwritePipelineArtifact(context: ArtifactUploadContext) {
        val pipelineSource = context.repoName == PIPELINE || context.repoName == CUSTOM
        if (!pipelineSource) {
            return
        }
        with(context.artifactInfo) {
            val existNode = nodeService.getNodeDetail(this)
            val metadata = resolveMetadata(context.request)
            val mPipelineId = metadata.find { it.key.equals(METADATA_SUB_PIPELINE_ID, true) }?.value?.toString()
                ?: metadata.find { it.key.equals(METADATA_PIPELINE_ID, true) }?.value?.toString()
            if (mPipelineId != null) {
                val status = checkPipelineArtifactUploadPermission(
                    artifactInfo = this,
                    existNode = existNode,
                    metadata = metadata
                )
                addPipelineMetadata(existNode, context, status)
            } else {
                checkNormalArtifactUploadPermission(existNode, projectId, repoName)
            }
        }
    }

    private fun addPipelineMetadata(
        existNode: NodeDetail?,
        context: ArtifactUploadContext,
        status: PipelineBuildStatus
    ) {
        val overwriteTime = existNode?.metadata?.get(METADATA_OVERWRITE_COUNT)?.toString()?.toInt() ?: 0
        existNode?.let {
            context.pipelineMetadata[METADATA_OVERWRITE_COUNT] = (overwriteTime + 1).toString()
        }
        context.pipelineMetadata[METADATA_UPLOAD_CHANNEL] =
            if (status.debug) UPLOAD_CHANNEL_PIPELINE_DEBUG else UPLOAD_CHANNEL_PIPELINE
        context.pipelineMetadata[METADATA_USER_ID] = status.startUser
    }

    /**
     * 检查流水线归档的上传权限
     * 1. 需要包含流水线元数据，projectId、pipelineId、buildId或buildNo、taskId(可选)
     * 2. 对应流水线需要是正在运行的状态
     * 3. 如果是全新上传，
     *      * 3.1.如果是流水线仓库，元数据pipelineId、buildId需要和路径一致
     *      * 3.2.如果是自定义仓库，则允许上传
     * 4. 如果是覆盖上传
     *      * 4.1.如果是流水线仓库，buildId需要和原节点一致
     *      * 4.1.如果是自定义仓库，则允许覆盖
     */
    @Suppress("ComplexMethod")
    fun checkPipelineArtifactUploadPermission(
        artifactInfo: ArtifactInfo,
        existNode: NodeDetail?,
        metadata: List<MetadataModel>,
    ): PipelineBuildStatus {
        val subProjectId = metadata.find { it.key.equals(METADATA_SUB_PROJECT_ID, true) }?.value?.toString()
        val subPipelineId = metadata.find { it.key.equals(METADATA_SUB_PIPELINE_ID, true) }?.value?.toString()
        val subBuildId = metadata.find { it.key.equals(METADATA_SUB_BUILD_ID, true) }?.value?.toString()
        val projectId = metadata.find { it.key.equals(METADATA_PROJECT_ID, true) }?.value?.toString()
        val pipelineId = metadata.find { it.key.equals(METADATA_PIPELINE_ID, true) }?.value?.toString()
        val buildId = metadata.find { it.key.equals(METADATA_BUILD_ID, true) }?.value?.toString()
        val folderBuildId = metadata.find { it.key.equals(METADATA_FOLDER_BUILD_ID, true) }?.value?.toString()
        if (projectId == null) {
            ciPermissionManager.throwOrLogError(
                messageCode = GenericMessageCode.PIPELINE_METADATA_INCOMPLETE,
                "projectId"
            )
            return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
        }
        if (pipelineId == null) {
            ciPermissionManager.throwOrLogError(
                messageCode = GenericMessageCode.PIPELINE_METADATA_INCOMPLETE,
                "pipelineId"
            )
            return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
        }
        if (buildId == null && folderBuildId == null) {
            ciPermissionManager.throwOrLogError(
                messageCode = GenericMessageCode.PIPELINE_METADATA_INCOMPLETE,
                "buildId"
            )
            return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
        }
        val status = ciPermissionManager.checkPipelineRunningStatus(
            projectId = subProjectId ?: projectId,
            pipelineId = subPipelineId ?: pipelineId,
            buildId = subBuildId ?: buildId ?: folderBuildId
        )
        if (artifactInfo.repoName == CUSTOM) {
            return status
        }
        if (existNode == null) {
            val folders = artifactInfo.getArtifactFullPath().split(StringPool.SLASH)
            if (folders.size < 4) {
                ciPermissionManager.throwOrLogError(GenericMessageCode.PIPELINE_REPO_MANUAL_UPLOAD_NOT_ALLOWED)
                return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
            }
            val pPipelineId = folders[1]
            val pBuildId = folders[2]
            if (pPipelineId != pipelineId || pBuildId != (buildId ?: folderBuildId)) {
                ciPermissionManager.throwOrLogError(
                    messageCode = GenericMessageCode.PIPELINE_ARTIFACT_PATH_ILLEGAL,
                    artifactInfo.getArtifactFullPath(), "${artifactInfo.projectId}/$pPipelineId/$pBuildId"
                )
                return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
            }
            return status
        } else {
            val existBuildIdKey = existNode.metadata.keys.find { it.equals(METADATA_BUILD_ID, true) }
            val diffBuildId = existNode.metadata[existBuildIdKey] != buildId
            if (existNode.repoName == PIPELINE && diffBuildId) {
                ciPermissionManager.throwOrLogError(
                    messageCode = GenericMessageCode.PIPELINE_ARTIFACT_OVERWRITE_NOT_ALLOWED,
                    "${existNode.projectId}${existNode.path.removeSuffix(StringPool.SLASH)}"
                )
                return PipelineBuildStatus(SecurityUtils.getUserId(), false, "RUNNING")
            }
            return status
        }
    }

    /**
     * 检查普通上传的权限
     * 1.如果是流水线仓库，则禁止上传
     * 2.如果是自定义仓库，并且原节点是流水线归档的制品，则禁止覆盖
     */
    fun checkNormalArtifactUploadPermission(
        existNode: NodeDetail?,
        projectId: String,
        repoName: String
    ) {
        if (repoName == PIPELINE) {
            return ciPermissionManager.throwOrLogError(GenericMessageCode.PIPELINE_REPO_MANUAL_UPLOAD_NOT_ALLOWED)
        }
        existNode?.metadata?.keys?.forEach { key ->
            if (CIPermissionManager.PIPELINE_METADATA.any { it.equals(key, true) }) {
                val mProjectId = existNode.metadata[METADATA_PROJECT_ID]
                    ?: existNode.metadata[METADATA_PROJECT_ID.lowercase(Locale.getDefault())]
                val mPipelineId = existNode.metadata[METADATA_PIPELINE_ID]
                    ?: existNode.metadata[METADATA_PIPELINE_ID.lowercase(Locale.getDefault())]
                val mBuildId = existNode.metadata[METADATA_BUILD_ID]
                    ?: existNode.metadata[METADATA_BUILD_ID.lowercase(Locale.getDefault())]
                return ciPermissionManager.throwOrLogError(
                    messageCode = GenericMessageCode.CUSTOM_ARTIFACT_OVERWRITE_NOT_ALLOWED,
                    existNode.fullPath, "$mProjectId/$mPipelineId/$mBuildId"
                )
            }
        }
    }

    /**
     * 单节点下载，支持目录下载
     */
    private fun downloadSingleNode(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val node = getNodeDetailsFromReq(true)?.firstOrNull()
                ?: ArtifactContextHolder.getNodeDetail(artifactInfo)
                ?: return null
            if (node.folder) {
                return downloadFolder(this, node)
            }
            downloadIntercept(this, node)
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()

            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    /**
     * 多节点下载, 支持同时下载文件和目录
     */
    private fun downloadMultiNode(context: ArtifactDownloadContext): ArtifactResource {
        with(context) {
            val fullPathList = artifacts!!.map { it.getArtifactFullPath() }
            val commonParent = PathUtils.getCommonParentPath(fullPathList)
            val nodes = getNodeDetailsFromReq(true)
                ?: queryNodeDetailList(
                    projectId = projectId,
                    repoName = repoName,
                    paths = fullPathList,
                    prefix = commonParent
                )
            val notExistNodes = fullPathList.subtract(nodes.map { it.fullPath })
            if (notExistNodes.isNotEmpty()) {
                throw NodeNotFoundException(notExistNodes.joinToString(StringPool.COMMA))
            }
            val (folderNodes, fileNodes) = nodes.partition { it.folder }
            // 添加所有目录节点的子节点, 检查文件数量和大小总和, 下载拦截
            val allNodes = getSubNodes(
                context = context,
                folders = folderNodes,
                includeFolder = true,
                initialCount = fileNodes.size.toLong(),
                initialSize = fileNodes.sumOf { it.size }
            ) + nodes
            // 通过给每个ZipEntry.name添加或移除公共前缀, 改变zip包内的目录结构
            // 如果多个节点没有公共目录，例如下载/file1和/file2时, 根据repoName添加公共前缀, 给zip包添加一个以repoName命名的顶层目录
            // 目的是保证zip包解压缩出来只有1个目录, 没有顶层目录时, 有些解压缩工具有可能会直接在当前目录解压出多个文件或目录
            val prefixToAdd = if (PathUtils.isRoot(commonParent) && fullPathList.size > 1) "$repoName/" else ""
            // 去掉多余的公共目录层级, 例如下载/a/b/c/file1和/a/b/c/file2时, 移除前缀/a/b/, 使得zip包的顶层目录为c
            val prefixToRemove = if (fullPathList.size > 1) PathUtils.resolveParent(commonParent) else commonParent
            // 支持下载空目录
            val emptyStream = ArtifactInputStream(EmptyInputStream.INSTANCE, Range.full(0))
            val nodeMap = allNodes.associateBy(
                keySelector = {
                    prefixToAdd + it.fullPath.removePrefix(prefixToRemove) + if (it.folder) StringPool.SLASH else ""
                },
                valueTransform = {
                    if (it.folder) emptyStream else {
                        storageManager.loadArtifactInputStream(it, storageCredentials)
                            ?: throw ArtifactNotFoundException(it.fullPath)
                    }
                }
            )
            // 添加node是为了下载单个空目录的时候以zip包的形式下载, 否则下载的是一个空文件而不是目录
            val node = if (allNodes.size == 1) allNodes.first() else null
            return ArtifactResource(nodeMap, node, useDisposition = true, nodes = allNodes)
        }
    }

    private fun getSubNodes(
        context: ArtifactDownloadContext,
        folders: List<NodeDetail>,
        includeFolder: Boolean = false,
        initialCount: Long = 0,
        initialSize: Long = 0
    ): List<NodeDetail> {
        var totalSize = initialSize
        val nodes = mutableListOf<NodeDetail>()
        folders.forEach { folder ->
            var pageNumber = 1
            do {
                val option = NodeListOption(
                    pageNumber = pageNumber,
                    pageSize = PAGE_SIZE,
                    includeFolder = includeFolder,
                    includeMetadata = true,
                    deep = true
                )
                val records =
                    nodeService.listNodePage(ArtifactInfo(folder.projectId, folder.repoName, folder.fullPath), option)
                    .records.takeUnless { it.isEmpty() }?.map { NodeDetail(it) } ?: break
                records.filterNot { it.folder }.forEach { downloadIntercept(context, it) }
                totalSize += records.sumOf { it.size }
                checkFileTotalSize(totalSize)
                nodes.addAll(records)
                checkFileCount(initialCount + nodes.size)
                pageNumber++
            } while (records.size == PAGE_SIZE)
        }
        return nodes
    }

    private fun queryNodeDetailList(
        projectId: String,
        repoName: String,
        paths: List<String>,
        prefix: String
    ): List<NodeDetail> {
        var pageNumber = 1
        val nodeDetailList = mutableListOf<NodeDetail>()
        do {
            val option = NodeListOption(
                pageNumber = pageNumber,
                pageSize = PAGE_SIZE,
                includeFolder = true,
                includeMetadata = true,
                deep = true
            )
            val records = nodeService.listNodePage(ArtifactInfo(projectId, repoName, prefix), option).records
            if (records.isEmpty()) {
                break
            }
            nodeDetailList.addAll(
                records.filter { paths.contains(it.fullPath) }.map { NodeDetail(it) }
            )
            pageNumber++
        } while (nodeDetailList.size < paths.size)
        return nodeDetailList
    }

    /**
     * 下载目录
     * @param context 构件下载context
     * @param node 目录节点详情
     */
    private fun downloadFolder(context: ArtifactDownloadContext, node: NodeDetail): ArtifactResource? {
        // 查询子节点, 检查文件数量和目录大小, 下载拦截
        val nodes = getSubNodes(context, listOf(node))
        // 构造name-node map
        val prefix = "${node.fullPath}/"
        val nodeMap = nodes.associate {
            val name = it.fullPath.removePrefix(prefix)
            val inputStream = storageManager.loadArtifactInputStream(it, context.storageCredentials) ?: return null
            name to inputStream
        }
        return ArtifactResource(nodeMap, node, useDisposition = true, nodes = nodes)
    }

    private fun getNodeDetailsFromReq(allowFolder: Boolean): List<NodeDetail>? {
        val nodeDetailList = HttpContextHolder.getRequest().getAttribute(NODE_DETAIL_LIST_KEY) as? List<NodeDetail>
        nodeDetailList?.forEach {
            if (!allowFolder && it.folder) {
                throw BadRequestException(GenericMessageCode.DOWNLOAD_DIR_NOT_ALLOWED)
            }
        }
        return nodeDetailList
    }

    /**
     * 检查文件数量是否超过阈值
     * @throws ErrorCodeException 超过阈值抛出NODE_LIST_TOO_LARGE类型ErrorCodeException
     */
    @Throws(ErrorCodeException::class)
    private fun checkFileCount(fileCount: Long) {
        // 判断节点数量
        if (fileCount > BATCH_DOWNLOAD_COUNT_THRESHOLD) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_LIST_TOO_LARGE)
        }
    }

    /**
     * 检查数据大小是否超过阈值
     * @throws ErrorCodeException 超过阈值抛出NODE_LIST_TOO_LARGE类型ErrorCodeException
     */
    @Throws(ErrorCodeException::class)
    private fun checkFileTotalSize(totalSize: Long) {
        if (totalSize > BATCH_DOWNLOAD_SIZE_THRESHOLD) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_LIST_TOO_LARGE)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo) {
            val node = nodeService.getNodeDetail(this)
                ?: throw NodeNotFoundException(this.getArtifactFullPath())
            if (node.folder) {
                if (nodeService.countFileNode(this) > 0) {
                    throw ErrorCodeException(ArtifactMessageCode.FOLDER_CONTAINS_FILE)
                }
            }
            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, getArtifactFullPath(), context.userId)
            nodeService.deleteNode(nodeDeleteRequest)
        }
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return super.buildNodeCreateRequest(context).copy(
            expires = HeaderUtils.getLongHeader(HEADER_EXPIRES),
            overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
            nodeMetadata = resolveMetadata(context.request, context.pipelineMetadata)
        )
    }

    override fun onDownloadRedirect(context: ArtifactDownloadContext): Boolean {
        return redirectManager.redirect(context)
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val artifactInfo = context.artifactInfo
        return nodeService.getNodeDetail(artifactInfo)
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        require(context is GenericArtifactSearchContext)
        val queryModel = context.queryModel ?: return emptyList()
        val isSearchPipelineRoot = isSearchPipelineRoot(context)

        return if (isSearchPipelineRoot) {
            // 仅在查询流水线仓库第一页时返回用户有权限的流水线目录
            if (queryModel.page.pageNumber == DEFAULT_PAGE_NUMBER) {
                val userId = SecurityUtils.getUserId()
                pipelineNodeService.listPipeline(userId, context.projectId, context.repoName).map { node ->
                    val nodePropMap = LinkedHashMap<String, Any?>()
                    NodeInfo::class.memberProperties
                        .filter { it.name != NodeInfo::deleted.name }
                        .associateTo(nodePropMap) { Pair(it.name, it.get(node)) }
                    nodePropMap[RepositoryInfo::category.name] = RepositoryCategory.LOCAL.name
                    nodePropMap
                }
            } else {
                emptyList()
            }
        } else {
            // 强制替换为请求的projectId与repoName避免越权
            val newRule = replaceProjectIdAndRepo(queryModel.rule, context.projectId, context.repoName)
            nodeSearchService.searchWithoutCount(queryModel.copy(rule = newRule)).records.onEach { node ->
                (node as MutableMap<String, Any?>)[RepositoryInfo::category.name] = RepositoryCategory.LOCAL.name
            }
        }
    }

    /**
     * 请求类似下方例子时，将作为前端首次进入流水线仓库的请求
     *
     * {
     *   ”projectId“: "xxx",
     *   "repoName": "pipeline",
     *   "path": "/",
     *   "folder": true // 可选
     * }
     */
    private fun isSearchPipelineRoot(context: GenericArtifactSearchContext): Boolean {
        val rule = context.queryModel?.rule
        if (rule !is Rule.NestedRule) {
            return false
        }
        var searchProject = false
        var searchPipeline = false
        var searchRoot = false
        rule.rules.filterIsInstance<Rule.QueryRule>().forEach { queryRule ->
            val field = queryRule.field
            val value = queryRule.value
            searchProject = searchProject || field == NodeDetail::projectId.name
            searchPipeline = searchPipeline || field == NodeDetail::repoName.name && value in PIPELINE_REPO_NAME
            searchRoot = searchRoot || field == NodeDetail::path.name && value == ROOT
        }
        return searchProject && searchPipeline && searchRoot
    }

    /**
     * 判断是否为分块上传
     */
    private fun isBlockUpload(uploadId: String?, sequence: Int?): Boolean {
        return !uploadId.isNullOrBlank() && sequence != null
    }

    /**
     * 上传分块
     */
    private fun blockUpload(uploadId: String, sequence: Int, context: ArtifactUploadContext) {
        with(context) {
            if (!storageService.checkBlockId(uploadId, storageCredentials)) {
                throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
            }
            val blockAppend = HeaderUtils.getHeader(HEADER_BLOCK_APPEND)?.toBoolean() ?: false
            val range = HttpRangeUtils.resolveContentRange(HeaderUtils.getHeader(CONTENT_RANGE))
            val totalSize = HeaderUtils.getHeader(HEADER_SIZE)?.toLongOrNull()
            if (blockAppend && range != null && totalSize != null) {
                storageService.storeBlockWithRandomPosition(
                    uploadId,
                    sequence,
                    getArtifactSha256(),
                    getArtifactFile(),
                    HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
                    storageCredentials,
                    startPosition = range.start,
                    totalLength = totalSize
                )
            } else {
                storageService.storeBlock(
                    uploadId,
                    sequence,
                    getArtifactSha256(),
                    getArtifactFile(),
                    HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
                    storageCredentials
                )
            }
        }
    }

    /**
     * 从header中提取metadata
     */
    fun resolveMetadata(
        request: HttpServletRequest,
        pipelineMetadata: Map<String, String>? = null
    ): List<MetadataModel> {
        val metadata = mutableMapOf<String, String>()
        // case insensitive
        val headerNames = request.headerNames
        for (headerName in headerNames) {
            if (headerName.startsWith(BKREPO_META_PREFIX, true)) {
                val key = headerName.substring(BKREPO_META_PREFIX.length).trim().lowercase(Locale.getDefault())
                if (key.isNotBlank()) {
                    metadata[key] = HeaderUtils.getUrlDecodedHeader(headerName)!!
                }
            }
        }
        // case sensitive, base64 metadata
        // format X-BKREPO-META: base64(a=1&b=2)
        request.getHeader(BKREPO_META)?.let { metadata.putAll(decodeMetadata(it)) }
        pipelineMetadata?.let { metadata.putAll(pipelineMetadata) }
        return metadata.map { MetadataModel(key = it.key, value = it.value) }
    }

    private fun decodeMetadata(header: String): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        try {
            val metadataUrl = String(Base64.getDecoder().decode(header))
            metadataUrl.split(CharPool.AND).forEach { part ->
                val pair = part.trim().split(CharPool.EQUAL, limit = 2)
                if (pair.size > 1 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                    val key = URLDecoder.decode(pair[0], StringPool.UTF_8)
                    val value = URLDecoder.decode(pair[1], StringPool.UTF_8)
                    metadata[key] = value
                }
            }
        } catch (exception: IllegalArgumentException) {
            logger.warn("$header is not in valid Base64 scheme.")
        }
        return metadata
    }


    /**
     * 是否使用分块追加上传
     */
    private fun isChunkedUpload(uploadType: String?): Boolean {
        return !uploadType.isNullOrEmpty() && uploadType == CHUNKED_UPLOAD
    }

    private fun chunkedUpload(context: ArtifactUploadContext) {
        logger.info("chunked upload method ${context.request.method} ")
        val responseProperty = when (context.request.method) {
            HttpMethod.PATCH.name -> patchUpload(context)
            HttpMethod.PUT.name -> putUpload(context)
            else -> null
        } ?: return
        uploadResponse(responseProperty, context.response)
    }

    private fun patchUpload(context: ArtifactUploadContext): ChunkedResponseProperty? {
        require(context.artifactInfo is GenericChunkedArtifactInfo)
        with(context.artifactInfo as GenericChunkedArtifactInfo) {
            val range = context.request.getHeader(CONTENT_RANGE)
            val length = context.request.contentLength
            logger.info(
                "The file with range $range and length $length in repo $projectId|$repoName " +
                    "is being uploaded with uuid: $uuid"
            )

            val lengthOfAppendFile = storageService.findLengthOfAppendFile(
                uuid!!, context.repositoryDetail.storageCredentials
            )
            logger.info("current length of append file is $lengthOfAppendFile")
            val (patchLen, status) = when (ChunkedUploadUtils.chunkedRequestCheck(
                lengthOfAppendFile = lengthOfAppendFile,
                range = range,
                contentLength = length
            )) {
                ChunkedUploadUtils.RangeStatus.ILLEGAL_RANGE -> {
                    Pair(length.toLong(), HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                }
                ChunkedUploadUtils.RangeStatus.READY_TO_APPEND -> {
                    val patchLen = storageService.append(
                        appendId = uuid!!,
                        artifactFile = context.getArtifactFile(),
                        storageCredentials = context.repositoryDetail.storageCredentials
                    )
                    logger.info(
                        "Part of file with sha256 $sha256 in repo $projectId|$repoName " +
                            "has been uploaded, size of append file is $patchLen and uuid: $uuid"
                    )
                    Pair(patchLen, HttpStatus.ACCEPTED)
                }
                else -> {
                    logger.info(
                        "Part of file with sha256 $sha256 in repo $projectId|$repoName " +
                            "already appended, size of append file is $lengthOfAppendFile and uuid: $uuid"
                    )
                    Pair(lengthOfAppendFile, HttpStatus.ACCEPTED)
                }
            }
            return ChunkedResponseProperty(
                status = status,
                size = patchLen,
                uuid = uuid!!
            )
        }
    }

    /**
     * blob PUT上传的逻辑处理
     * 1 blob POST with PUT 上传的put模块处理
     * 2 blob POST PATCH with PUT 上传的put模块处理
     */
    private fun putUpload(context: ArtifactUploadContext): ChunkedResponseProperty {
        require(context.artifactInfo is GenericChunkedArtifactInfo)
        with(context.artifactInfo as GenericChunkedArtifactInfo) {
            storageService.append(
                appendId = uuid!!,
                artifactFile = context.getArtifactFile(),
                storageCredentials = context.repositoryDetail.storageCredentials
            )

            // 当传递了 md5和size 以后，分块文件合并时不计算 sha256 与 md5,只校验 size 是否一致
            val originalFileInfo = if (sha256 != null && md5 != null) {
                FileInfo(sha256!!, md5!!, size!!)
            } else {
                null
            }
            val fileInfo = try {
                storageService.finishAppend(
                    uuid!!, context.repositoryDetail.storageCredentials, originalFileInfo
                )
            } catch (e: StorageErrorException) {
                throw BadRequestException(GenericMessageCode.CHUNKED_ARTIFACT_BROKEN, sha256.orEmpty())
            }
            logger.info(
                "The file with sha256 $sha256 in repo $projectId|$repoName " +
                    "has been uploaded with uuid: $uuid"
            )
            val property = ChunkedResponseProperty(
                status = HttpStatus.CREATED,
                uuid = uuid,
                contentLength = 0
            )
            val nodeRequest = buildNodeCreateRequest(context).copy(
                sha256 = fileInfo.sha256,
                md5 = fileInfo.md5,
                size = fileInfo.size
            )
            ActionAuditContext.current().setInstance(nodeRequest)
            nodeService.createNode(nodeRequest)
            return property
        }
    }

    private fun calculateExpiryDateTime(expireDuration: Duration): LocalDateTime {
        val hoursToAdd = expireDuration.toHours().takeIf { it > 0 } ?: 12 // 如果 expireDuration <= 0，则使用 12 小时
        return LocalDateTime.now().plusHours(hoursToAdd)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(GenericLocalRepository::class.java)

        /**
         * 目录下载或批量下载，文件数量阈值
         */
        private const val BATCH_DOWNLOAD_COUNT_THRESHOLD = 1024

        /**
         * 目录下载或批量下载，数据大小阈值
         */
        private val BATCH_DOWNLOAD_SIZE_THRESHOLD = DataSize.ofGigabytes(10).toBytes()

        private const val REPORT = "report"

        /**
         * 文件上传请求参数，是否需要同步至Commit Edge组网模式下的边缘节点
         */
        private const val PARAM_REPLICATE = "replicate"

        /**
         * 节点分页查询单页大小
         */
        private const val PAGE_SIZE = 1000

        private val PIPELINE_REPO_NAME = listOf(PIPELINE, "$PIPELINE-devx")

        private const val UPLOAD_CHANNEL_PIPELINE = "pipeline"
        private const val UPLOAD_CHANNEL_PIPELINE_DEBUG = "pipeline-debug"
    }
}
