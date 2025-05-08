/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.handler

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.NodeAttribute
import com.tencent.bkrepo.common.metadata.model.NodeAttribute.Companion.DEFAULT_MODE
import com.tencent.bkrepo.common.metadata.model.NodeAttribute.Companion.NOBODY
import com.tencent.bkrepo.common.metadata.service.fs.FsService
import com.tencent.bkrepo.common.metadata.service.metadata.RMetadataService
import com.tencent.bkrepo.common.metadata.service.repo.RRepositoryService
import com.tencent.bkrepo.common.storage.core.overlay.OverlayRangeUtils
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.request.ChangeAttributeRequest
import com.tencent.bkrepo.fs.server.request.LinkRequest
import com.tencent.bkrepo.fs.server.request.MoveRequest
import com.tencent.bkrepo.fs.server.request.NodePageRequest
import com.tencent.bkrepo.fs.server.request.NodeRequest
import com.tencent.bkrepo.fs.server.request.SetLengthRequest
import com.tencent.bkrepo.fs.server.resolveRange
import com.tencent.bkrepo.fs.server.response.StatResponse
import com.tencent.bkrepo.fs.server.service.FileNodeService
import com.tencent.bkrepo.fs.server.service.node.RNodeService
import com.tencent.bkrepo.fs.server.toNode
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSetLengthRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import reactivefeign.client.ReadTimeoutException

/**
 * 节点操作相关的处理器
 *
 * 处理节点操作的请求
 * */
class NodeOperationsHandler(
    private val fileNodeService: FileNodeService,
    private val fsService: FsService,
    private val nodeService: RNodeService,
    private val repositoryService: RRepositoryService,
    private val metadataService: RMetadataService
) {

    suspend fun getNode(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val nodeDetail = nodeService.getNodeDetail(
                ArtifactInfo(projectId, repoName, fullPath)
            ) ?: return ServerResponse.notFound().buildAndAwait()
            return ReactiveResponseBuilder.success(nodeDetail.nodeInfo.toNode())
        }
    }

    suspend fun listNodes(request: ServerRequest): ServerResponse {
        val pageRequest = NodePageRequest(request)
        with(pageRequest) {
            val nodes = nodeService.listNodePage(
                artifact = ArtifactInfo(projectId, repoName, fullPath),
                option = listOption
            ).records.map { it.toNode() }.toList()
            return ReactiveResponseBuilder.success(nodes)
        }
    }

    /**
     * 删除节点
     * */
    suspend fun deleteNode(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val nodeDeleteRequest = NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                operator = ReactiveSecurityUtils.getUser()
            )
            nodeService.deleteNode(nodeDeleteRequest)
            fileNodeService.deleteNodeBlocks(projectId, repoName, fullPath)
            return ReactiveResponseBuilder.success()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun changeAttribute(request: ServerRequest): ServerResponse {
        with(ChangeAttributeRequest(request)) {
            val preFsAttributeStr = metadataService.listMetadata(projectId, repoName, fullPath)[FS_ATTR_KEY]
            val attrMap = preFsAttributeStr as? Map<String, Any> ?: mapOf()
            val preFsAttribute = NodeAttribute(
                uid = attrMap[NodeAttribute::uid.name] as? String ?: NOBODY,
                gid = attrMap[NodeAttribute::gid.name] as? String ?: NOBODY,
                mode = attrMap[NodeAttribute::mode.name] as? Int ?: DEFAULT_MODE,
                flags = attrMap[NodeAttribute::flags.name] as? Int,
                rdev = attrMap[NodeAttribute::rdev.name] as? Int,
                type = attrMap[NodeAttribute::type.name] as? Int
            )

            val attributes = NodeAttribute(
                uid = uid ?: preFsAttribute.uid,
                gid = gid ?: preFsAttribute.gid,
                mode = mode ?: preFsAttribute.mode,
                flags = flags ?: preFsAttribute.flags,
                rdev = rdev ?: preFsAttribute.rdev,
                type = type ?: preFsAttribute.type
            )
            val fsAttr = MetadataModel(
                key = FS_ATTR_KEY,
                value = attributes
            )
            val saveMetaDataRequest = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                nodeMetadata = listOf(fsAttr),
                operator = ReactiveSecurityUtils.getUser()
            )
            metadataService.saveMetadata(saveMetaDataRequest)
            return ReactiveResponseBuilder.success(attributes)
        }
    }

    suspend fun getStat(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val cap = ReactiveArtifactContextHolder.getRepoDetail().quota
            val nodeStat = try {
                repositoryService.statRepo(projectId, repoName)
            } catch (e: ReadTimeoutException) {
                logger.warn("get repo[$projectId/$repoName] stat timeout")
                NodeSizeInfo(0, 0, UNKNOWN)
            }

            val res = StatResponse(
                subNodeCount = nodeStat.subNodeCount,
                size = nodeStat.size,
                capacity = cap ?: UNKNOWN
            )

            return ReactiveResponseBuilder.success(res)
        }
    }

    /**
     * 移动节点
     * */
    suspend fun move(request: ServerRequest): ServerResponse {
        with(MoveRequest(request)) {
            if (overwrite) {
                nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, dst))?.let {
                    val nodeDeleteRequest = NodeDeleteRequest(
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = dst,
                        operator = ReactiveSecurityUtils.getUser()
                    )
                    nodeService.deleteNode(nodeDeleteRequest)
                }
            }
            val moveRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = dst,
                operator = ReactiveSecurityUtils.getUser()
            )
            nodeService.renameNode(moveRequest)
            fileNodeService.renameNodeBlocks(projectId, repoName, fullPath, dst)
            return ReactiveResponseBuilder.success()
        }
    }

    suspend fun info(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val nodeDetail = nodeService.getNodeDetail(
                ArtifactInfo(projectId, repoName, fullPath)
            ) ?: return ServerResponse.notFound().buildAndAwait()
            val range = try {
                request.resolveRange(nodeDetail.size)
            } catch (e: IllegalArgumentException) {
                logger.info("read file[$projectId/$repoName$fullPath] failed: ${e.message}")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, HttpHeaders.RANGE)
            }
            val blocks = fileNodeService.info(nodeDetail, range)
            val newBlocks = OverlayRangeUtils.build(blocks, range)
            return ReactiveResponseBuilder.success(newBlocks)
        }
    }

    suspend fun createNode(request: ServerRequest): ServerResponse {
        val node = createNode(request, false)
        return ReactiveResponseBuilder.success(node.nodeInfo.toNode())
    }

    suspend fun mkdir(request: ServerRequest): ServerResponse {
        val node = createNode(request, true)
        return ReactiveResponseBuilder.success(node.nodeInfo.toNode())
    }

    suspend fun mknod(request: ServerRequest): ServerResponse {
        val node = createNode(request, false)
        return ReactiveResponseBuilder.success(node.nodeInfo.toNode())
    }

    suspend fun symlink(request: ServerRequest): ServerResponse {
        val nodeLinkRequest = with(LinkRequest(request)) {
            NodeLinkRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                targetProjectId = projectId,
                targetRepoName = repoName,
                targetFullPath = targetFullPath,
                checkTargetExist = false,
                operator = ReactiveSecurityUtils.getUser()
            )
        }
        val node = nodeService.link(nodeLinkRequest)
        return ReactiveResponseBuilder.success(node.nodeInfo.toNode())
    }

    private suspend fun createNode(request: ServerRequest, folder: Boolean): NodeDetail {
        with(NodeRequest(request)) {
            val user = ReactiveSecurityUtils.getUser()
            // 创建节点
            val attributes = NodeAttribute(
                uid = NOBODY,
                gid = NOBODY,
                mode = mode ?: DEFAULT_MODE,
                flags = flags,
                rdev = rdev,
                type = type
            )
            val fsAttr = MetadataModel(
                key = FS_ATTR_KEY,
                value = attributes
            )

            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = folder,
                fullPath = fullPath,
                sha256 = FAKE_SHA256,
                md5 = FAKE_MD5,
                nodeMetadata = listOf(fsAttr),
                operator = user
            )
            return fsService.createNode(nodeCreateRequest)
        }
    }

    suspend fun setLength(request: ServerRequest): ServerResponse {
        with(SetLengthRequest(request)) {
            val user = ReactiveSecurityUtils.getUser()
            val nodeSetLengthRequest = NodeSetLengthRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newLength = length,
                operator = user
            )
            fsService.setLength(nodeSetLengthRequest)
            return ReactiveResponseBuilder.success()
        }
    }

    companion object {
        private const val UNKNOWN = -1L
        private val logger = LoggerFactory.getLogger(NodeOperationsHandler::class.java)
    }
}
