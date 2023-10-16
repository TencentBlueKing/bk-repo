/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.repository.redirect

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.replication.api.ClusterNodeClient
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.time.Duration
import javax.servlet.http.HttpServletRequest

/**
 * 边缘节点重定向服务
 * */
@Service
@Order(1) // 优先级最高，属于其他集群的制品优先重定向到其他集群
class EdgeNodeRedirectService(
    private val clusterProperties: ClusterProperties,
    private val clusterNodeClient: ClusterNodeClient,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
) : DownloadRedirectService {

    /**
     * 重定向到默认集群节点
     * */
    override fun redirect(context: ArtifactDownloadContext) {
        getEdgeClusterName(context)?.let {
            redirectToSpecificCluster(context, it)
        }
    }

    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)
        val selfClusterName = clusterProperties.self.name
        if (logger.isDebugEnabled) {
            logger.debug("node cluster: ${node?.clusterNames.orEmpty().toJsonString()},in cluster $selfClusterName")
        }

        return !(node == null ||
                node.clusterNames.isNullOrEmpty() ||
                node.clusterNames!!.contains(selfClusterName) ||
                getEdgeClusterName(context) == null)
    }

    /**
     * 重定向到指定节点
     * */
    private fun redirectToSpecificCluster(downloadContext: ArtifactDownloadContext, clusterName: String) {
        // 节点来自其他集群，重定向到其他节点。
        val clusterInfo = clusterNodeClient.getCluster(clusterName).data
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, clusterName)
        val edgeDomain = getEdgeDomain(clusterInfo)
        val request = downloadContext.request
        val requestPath = buildPath(request)
        val queryString = buildQueryString(request)
        val token = createTempToken(downloadContext)
        val redirectUrl = "$edgeDomain$GENERIC_SERVICE_NAME$requestPath?token=$token&$queryString"
        downloadContext.response.sendRedirect(redirectUrl)
    }

    /**
     * 获取边缘节点名称
     * */
    private fun getEdgeClusterName(context: ArtifactDownloadContext): String? {
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)
        return node?.clusterNames?.firstOrNull()
    }

    /**
     * 获取边缘节点域名
     * */
    private fun getEdgeDomain(clusterInfo: ClusterNodeInfo): String {
        val url = clusterInfo.url
        if (url.endsWith(StringPool.SLASH)) {
            url.removeSuffix(StringPool.SLASH)
        }
        return url.removeSuffix(REPLICATION_SERVICE_NAME).ensureSuffix(StringPool.SLASH)
    }

    private fun createTempToken(downloadContext: ArtifactDownloadContext): String {
        with(downloadContext) {
            val createTokenRequest = TemporaryTokenCreateRequest(
                projectId = repositoryDetail.projectId,
                repoName = repositoryDetail.name,
                fullPathSet = setOf(artifactInfo.getArtifactFullPath()),
                expireSeconds = Duration.ofMinutes(5).seconds,
                type = TokenType.DOWNLOAD,
            )
            return temporaryTokenClient.createToken(createTokenRequest).data.orEmpty().first().token
        }
    }

    private fun buildQueryString(request: HttpServletRequest): String {
        val builder = StringBuilder()
        request.parameterMap.filterKeys { it != TOKEN }.forEach { (k, v) ->
            v.forEach {
                builder.append("$k=$it&")
            }
        }
        if (builder.isNotEmpty()) {
            return builder.removeSuffix("&").toString()
        }
        return builder.toString()
    }

    private fun buildPath(request: HttpServletRequest): String {
        if (request.requestURI.startsWith(TEMPORARY_REQUEST_PREFIX)) {
            return request.requestURI
        }
        if (request.requestURI.startsWith(SHARE_REQUEST_PREFIX)) {
            val newUri = request.requestURI.removePrefix(SHARE_REQUEST_PREFIX)
            return "$TEMPORARY_REQUEST_PREFIX$newUri"
        }
        if (request.requestURI.startsWith(REPO_LIST_REQUEST_PREFIX)) {
            val newUri = request.requestURI.removePrefix(REPO_LIST_REQUEST_PREFIX)
            return "$TEMPORARY_REQUEST_PREFIX$newUri"
        }
        return "$TEMPORARY_REQUEST_PREFIX${request.requestURI}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgeNodeRedirectService::class.java)
        const val REPLICATION_SERVICE_NAME = "replication"
        const val GENERIC_SERVICE_NAME = "generic"
        const val TOKEN = "token"
        const val TEMPORARY_REQUEST_PREFIX = "/temporary/download" // 临时链接下载前缀
        const val SHARE_REQUEST_PREFIX = "/api/share" // 共享链接下载前缀
        const val REPO_LIST_REQUEST_PREFIX = "/api/list" // 仓库列表下载前缀
    }
}
