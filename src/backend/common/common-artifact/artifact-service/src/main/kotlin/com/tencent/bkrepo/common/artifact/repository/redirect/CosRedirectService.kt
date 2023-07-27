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

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.determineMediaType
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.encodeDisposition
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.endpoint.DefaultEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import com.tencent.bkrepo.common.storage.innercos.request.CosRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.innercos.urlEncode
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 当使用对象存储作为后端存储时，支持创建对象的预签名下载URL，并将用户的下载请求重定向到该URL
 */
@Service
@Order(2)
class CosRedirectService(private val storageProperties: StorageProperties) : DownloadRedirectService {
    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        // 仅支持重定向本地单文件下载请求
        val node = ArtifactContextHolder.getNodeDetail()
        if (node == null || node.folder || context.artifacts?.isNotEmpty() == true) {
            return false
        }

        val greaterThanMinSize = node.size > storageProperties.response.minDirectDownloadSize.toBytes()
        val storageCredentials = context.storageCredentials
        val isInnerCosStorageCredentials = storageCredentials is InnerCosCredentials ||
                storageProperties.type == StorageType.INNERCOS
        val redirectTo = HttpContextHolder.getRequest().getHeader("X-BKREPO-DOWNLOAD-REDIRECT-TO")
        return isInnerCosStorageCredentials && redirectTo == RedirectTo.INNERCOS.name && greaterThanMinSize
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val credentials = context.storageCredentials ?: storageProperties.defaultStorageCredentials()
        require(credentials is InnerCosCredentials)
        val node = ArtifactContextHolder.getNodeDetail()!!

        // 创建请求并签名
        val clientConfig = ClientConfig(credentials).apply {
            signExpired = Duration.ofSeconds(DEFAULT_SIGN_EXPIRED_SECOND)
            // 重定向请求不使用北极星解析，直接使用域名
            endpointResolver = DefaultEndpointResolver()
            httpProtocol = HttpProtocol.HTTPS
        }
        val range = resolveRange(node.size)
        val request = GetObjectRequest(node.sha256!!, range?.start, range?.end)
        addCosResponseHeaders(context, request, node)
        val urlencodedSign = request.sign(credentials, clientConfig).urlEncode(true)
        if (request.parameters.isEmpty()) {
            request.url += "?sign=$urlencodedSign"
        } else {
            request.url += "&sign=$urlencodedSign"
        }

        logger.info(
            "redirect request of download to cos[${credentials.key}], " +
                    "project[${node.projectId}], repo[${node.repoName}], fullPath[${node.fullPath}]"
        )
        // 重定向
        context.response.sendRedirect(request.url)
    }

    private fun resolveRange(total: Long): Range? {
        return try {
            val request = HttpContextHolder.getRequest()
            if (request.getHeader(HttpHeaders.RANGE).isNullOrEmpty()) {
                null
            } else {
                HttpRangeUtils.resolveRange(request, total)
            }
        } catch (exception: IllegalArgumentException) {
            logger.warn("Failed to resolve http range: ${exception.message}")
            throw ErrorCodeException(
                status = HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                messageCode = CommonMessageCode.REQUEST_RANGE_INVALID
            )
        }
    }

    private fun addCosResponseHeaders(context: ArtifactDownloadContext, request: CosRequest, node: NodeDetail) {
        val filename = context.artifactInfo.getResponseName()
        val cacheControl = node.metadata[HttpHeaders.CACHE_CONTROL]?.toString()
            ?: node.metadata[HttpHeaders.CACHE_CONTROL.toLowerCase()]?.toString()
            ?: StringPool.NO_CACHE
        request.parameters["response-cache-control"] = cacheControl
        val mime = determineMediaType(filename, storageProperties.response.mimeMappings)
        request.parameters["response-content-type"] = mime

        if (context.useDisposition) {
            request.parameters["response-content-disposition"] = encodeDisposition(filename)
        }
    }

    companion object {
        private const val DEFAULT_SIGN_EXPIRED_SECOND = 3 * 60L
        private val logger = LoggerFactory.getLogger(CosRedirectService::class.java)
    }
}
