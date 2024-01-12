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

import com.github.benmanes.caffeine.cache.Caffeine
import com.tencent.bkrepo.common.storage.core.overlay.OverlayRangeUtils
import com.tencent.bkrepo.fs.server.api.NodeClient
import com.tencent.bkrepo.fs.server.api.RGenericClient
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.constant.FAKE_MD5
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.model.NodeAttribute
import com.tencent.bkrepo.fs.server.model.NodeAttribute.Companion.DEFAULT_MODE
import com.tencent.bkrepo.fs.server.model.NodeAttribute.Companion.NOBODY
import com.tencent.bkrepo.fs.server.request.ChangeAttributeRequest
import com.tencent.bkrepo.fs.server.request.LinkRequest
import com.tencent.bkrepo.fs.server.request.MoveRequest
import com.tencent.bkrepo.fs.server.request.NodePageRequest
import com.tencent.bkrepo.fs.server.request.NodeRequest
import com.tencent.bkrepo.fs.server.request.SetLengthRequest
import com.tencent.bkrepo.fs.server.resolveRange
import com.tencent.bkrepo.fs.server.response.StatResponse
import com.tencent.bkrepo.fs.server.service.FileNodeService
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
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import reactivefeign.client.ReadTimeoutException
import java.time.Duration

/**
 * 节点操作相关的处理器
 *
 * 处理节点操作的请求
 * */
class NodeOperationsHandler(
    rGenericClient: RGenericClient,
    private val rRepositoryClient: RRepositoryClient,
    private val fileNodeService: FileNodeService
) {
    private val nodeClient = NodeClient(rRepositoryClient, rGenericClient)

    suspend fun getNode(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val nodeDetail = nodeClient.getNodeDetail(
                projectId = projectId,
                repo = ReactiveArtifactContextHolder.getRepoDetail(),
                fullPath = fullPath,
                category = category
            )
            return nodeDetail
                ?.let { ReactiveResponseBuilder.success(it.nodeInfo.toNode()) }
                ?: ServerResponse.notFound().buildAndAwait()
        }
    }

    suspend fun listNodes(request: ServerRequest): ServerResponse {
        val pageRequest = NodePageRequest(request)
        with(pageRequest) {
            val nodes = nodeClient.listNodes(
                projectId = projectId,
                repo = ReactiveArtifactContextHolder.getRepoDetail(),
                path = fullPath,
                option = listOption
            )
            return nodes?.let { ReactiveResponseBuilder.success(it) } ?: ServerResponse.notFound().buildAndAwait()
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
            val nodeDeleteResult = rRepositoryClient.deleteNode(nodeDeleteRequest).awaitSingle().data
            if (nodeDeleteResult?.deletedNumber != 1L) {
                return ServerResponse.badRequest().buildAndAwait()
            }
            fileNodeService.deleteNodeBlocks(projectId, repoName, fullPath)
            return ReactiveResponseBuilder.success()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun changeAttribute(request: ServerRequest): ServerResponse {
        with(ChangeAttributeRequest(request)) {
            val preFsAttributeStr = rRepositoryClient.listMetadata(projectId, repoName, fullPath).awaitSingle().data
                ?.get(FS_ATTR_KEY)
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
            rRepositoryClient.saveMetadata(saveMetaDataRequest).awaitSingle()
            return ReactiveResponseBuilder.success(attributes)
        }
    }

    suspend fun getStat(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val cacheKey = "$projectId-$repoName-$fullPath"
            var res = statCache.getIfPresent(cacheKey)
            if (res == null) {
                val cap = ReactiveArtifactContextHolder.getRepoDetail().quota
                val nodeStat = try {
                    rRepositoryClient.statRepo(projectId, repoName).awaitSingle().data
                } catch (e: ReadTimeoutException) {
                    logger.warn("get repo[$projectId/$repoName] stat timeout")
                    NodeSizeInfo(0, 0, UNKNOWN)
                }

                res = StatResponse(
                    subNodeCount = nodeStat?.subNodeCount ?: UNKNOWN,
                    size = nodeStat?.size ?: UNKNOWN,
                    capacity = cap ?: UNKNOWN
                )
                statCache.put(cacheKey, res)
            }

            return ReactiveResponseBuilder.success(res)
        }
    }

    /**
     * 移动节点
     * */
    suspend fun move(request: ServerRequest): ServerResponse {
        with(MoveRequest(request)) {
            if (overwrite) {
                rRepositoryClient.getNodeDetail(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = dst
                ).awaitSingle().data?.let {
                    val nodeDeleteRequest = NodeDeleteRequest(
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = dst,
                        operator = ReactiveSecurityUtils.getUser()
                    )
                    rRepositoryClient.deleteNode(nodeDeleteRequest).awaitSingle()
                }
            }
            val moveRequest = NodeRenameRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newFullPath = dst,
                operator = ReactiveSecurityUtils.getUser()
            )
            rRepositoryClient.renameNode(moveRequest).awaitSingle()
            fileNodeService.renameNodeBlocks(projectId, repoName, fullPath, dst)
            return ReactiveResponseBuilder.success()
        }
    }

    suspend fun info(request: ServerRequest): ServerResponse {
        with(NodeRequest(request)) {
            val nodeDetail = nodeClient.getNodeDetail(
                projectId = projectId,
                repo = ReactiveArtifactContextHolder.getRepoDetail(),
                fullPath = fullPath,
                category = category
            ) ?: return ServerResponse.notFound().buildAndAwait()
            val range = request.resolveRange(nodeDetail.size)
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
        val node = rRepositoryClient.link(nodeLinkRequest).awaitSingle().data!!
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
            return rRepositoryClient.createNode(nodeCreateRequest).awaitSingle().data!!
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
            rRepositoryClient.setLength(nodeSetLengthRequest).awaitSingle()
            return ReactiveResponseBuilder.success()
        }
    }

    private val statCache = Caffeine.newBuilder()
        .maximumSize(1000).expireAfterWrite(Duration.ofDays(1)).build<String, StatResponse>()

    companion object {
        private const val UNKNOWN = -1L
        private val logger = LoggerFactory.getLogger(NodeOperationsHandler::class.java)
    }
}
