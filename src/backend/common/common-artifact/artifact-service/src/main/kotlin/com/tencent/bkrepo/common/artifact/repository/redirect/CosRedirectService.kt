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
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.endpoint.DefaultEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import com.tencent.bkrepo.common.storage.innercos.request.CosRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.innercos.urlEncode
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 当使用对象存储作为后端存储时，支持创建对象的预签名下载URL，并将用户的下载请求重定向到该URL
 */
@Service
@Order(2)
class CosRedirectService(
    private val storageProperties: StorageProperties,
    private val storageService: StorageService,
    private val permissionManager: PermissionManager,
) : DownloadRedirectService {
    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        if (!storageProperties.redirect.enabled) {
            return false
        }

        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)
        // 从request uri中获取artifact信息，artifact为null时表示非单制品下载请求，此时不支持重定向
        val artifact = ArtifactContextHolder.getArtifactInfo()
        // node为null时表示制品不存在，或者是Remote仓库的制品尚未被缓存，此时不支持重定向
        if (node == null ||
            node.folder ||
            artifact == null ||
            node.compressed == true || // 压缩文件不支持重定向
            node.archived == true // 归档文件不支持重定向
        ) {
            return false
        }

        // 判断存储类型是否支持重定向，文件大小是否达到重定向的限制
        val storageCredentials = context.repositoryDetail.storageCredentials
            ?: storageProperties.defaultStorageCredentials()
        val notInnerCosStorageCredentials = storageCredentials !is InnerCosCredentials
        val lessThanMinSize = node.size < storageProperties.redirect.minDirectDownloadSize.toBytes()
        if (notInnerCosStorageCredentials || lessThanMinSize) {
            return false
        }

        // 判断仓库配置是否支持重定向
        val redirectSettings = DownloadRedirectSettings.from(context.repositoryDetail.configuration)
        var repoSupportRedirectTo = redirectSettings?.redirectTo == RedirectTo.INNERCOS.name
        if (repoSupportRedirectTo && redirectSettings?.fullPathRegex?.isNotEmpty() == true) {
            val regex = redirectSettings.fullPathRegex.toRegex()
            repoSupportRedirectTo = regex.matches(node.fullPath)
        }

        val redirectTo = HttpContextHolder.getRequest().getHeader("X-BKREPO-DOWNLOAD-REDIRECT-TO")
        val needToRedirect = repoSupportRedirectTo ||
            redirectTo == RedirectTo.INNERCOS.name ||
            storageProperties.redirect.redirectAllDownload

        // 文件存在于COS上时才会被重定向
        return needToRedirect && isSystemOrAdmin() && guessFileExists(node, storageCredentials)
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val credentials = context.repositoryDetail.storageCredentials ?: storageProperties.defaultStorageCredentials()
        require(credentials is InnerCosCredentials)
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)!!

        // 创建请求并签名
        val clientConfig = ClientConfig(credentials).apply {
            signExpired = storageProperties.redirect.redirectUrlExpireTime
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

        // 重定向
        logger.info("Redirect request of download node[${node.sha256}] to cos[${credentials.key}]")
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
                messageCode = CommonMessageCode.REQUEST_RANGE_INVALID,
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

    /**
     * 推测文件是否在COS上存在
     */
    private fun guessFileExists(node: NodeDetail, storageCredentials: StorageCredentials): Boolean {
        // 判断文件存在时间，文件存在时间超过预期的上传耗时则认为文件已上传到COS，避免频繁请求COS判断文件是否存在
        val createdDateTime = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val existsDuration = Duration.between(createdDateTime, LocalDateTime.now())
        val expectedUploadSeconds = node.size / storageProperties.redirect.uploadSizePerSecond.toBytes()
        if (existsDuration.seconds > expectedUploadSeconds) {
            return true
        }

        // 判断文件是否已经上传到COS
        logger.info("Checking node[${node.sha256}] exist in cos, createdDateTime[${node.createdDate}]")
        return storageService.exist(node.sha256!!, storageCredentials)
    }

    private fun isSystemOrAdmin(): Boolean {
        val userId = SecurityUtils.getUserId()
        return userId == SYSTEM_USER || permissionManager.isAdminUser(SecurityUtils.getUserId())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CosRedirectService::class.java)
    }
}
