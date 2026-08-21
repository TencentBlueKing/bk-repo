/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.BKREPO_CLIENT_NAME
import com.tencent.bkrepo.common.artifact.constant.HEADER_BKREPO_CLIENT
import com.tencent.bkrepo.common.artifact.constant.HEADER_BLOCK_MANIFEST
import com.tencent.bkrepo.common.artifact.constant.HEADER_DOWNLOAD_REDIRECT_TO
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.determineMediaType
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.encodeDisposition
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
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
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 当使用对象存储作为后端存储时，支持创建对象的预签名下载URL，并将用户的下载请求重定向到该URL
 */
@Service
@Order(2)
class CosRedirectService(
    private val storageProperties: StorageProperties,
    private val storageService: StorageService,
    private val blockNodeService: BlockNodeService,
) : DownloadRedirectService {

    /**
     * 配置门控（不查 node）。仅供 remapper 在路径解析前调用；判定结果不等价于 [doShouldRedirect]。
     */
    override fun mayRedirect(context: ArtifactDownloadContext): Boolean {
        if (!storageProperties.redirect.enabled) {
            return false
        }
        val storageCredentials = context.repositoryDetail.storageCredentials
            ?: storageProperties.defaultStorageCredentials()
        if (storageCredentials !is InnerCosCredentials) {
            return false
        }
        if (!isProjectAllowed(context.projectId)) {
            return false
        }
        val redirectSettings = DownloadRedirectSettings.from(context.repositoryDetail.configuration)
        val repoSupportRedirectTo = redirectSettings?.redirectTo == RedirectTo.INNERCOS.name
        return policyAllowsRedirect(context, repoSupportRedirectTo)
    }

    override fun doShouldRedirect(context: ArtifactDownloadContext): Boolean {
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
            node.compressed == true ||
            node.archived == true
        ) {
            return false
        }
        val isFake = node.sha256 == FAKE_SHA256
        if (isFake && !isArtifactClient()) {
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

        val needToRedirect = policyAllowsRedirect(context, repoSupportRedirectTo)
        if (!needToRedirect || !isProjectAllowed(context.projectId)) {
            return false
        }
        if (isFake) {
            return loadContiguousBlocks(node) != null
        }
        return guessFileExists(node, storageCredentials)
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val credentials = context.repositoryDetail.storageCredentials ?: storageProperties.defaultStorageCredentials()
        require(credentials is InnerCosCredentials)
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)!!
        val clientConfig = ClientConfig(credentials).apply {
            signExpired = storageProperties.redirect.redirectUrlExpireTime
            endpointResolver = DefaultEndpointResolver()
            httpProtocol = HttpProtocol.HTTPS
        }
        if (node.sha256 == FAKE_SHA256) {
            writeBlockManifest(context, node, credentials, clientConfig)
            return
        }
        val request = GetObjectRequest(node.sha256!!)
        addCosResponseHeaders(context, request, node)
        context.response.sendRedirect(signCosUrl(request, credentials, clientConfig))
        logger.info("Redirect request of download node[${node.sha256}] to cos[${credentials.key}]")
    }

    private fun addCosResponseHeaders(context: ArtifactDownloadContext, request: CosRequest, node: NodeDetail) {
        val filename = context.artifactInfo.getResponseName()
        val cacheControl = node.metadata[HttpHeaders.CACHE_CONTROL]?.toString()
            ?: node.metadata[HttpHeaders.CACHE_CONTROL.lowercase(Locale.getDefault())]?.toString()
            ?: StringPool.NO_CACHE
        request.parameters["response-cache-control"] = cacheControl
        val mime = context.getStringAttribute(ATTR_RESPONSE_CONTENT_TYPE)
            ?: determineMediaType(filename, storageProperties.response.mimeMappings)
        request.parameters["response-content-type"] = mime

        if (context.useDisposition) {
            request.parameters["response-content-disposition"] = encodeDisposition(filename)
        }
    }

    private fun writeBlockManifest(
        context: ArtifactDownloadContext,
        node: NodeDetail,
        credentials: InnerCosCredentials,
        clientConfig: ClientConfig,
    ) {
        val blocks = loadContiguousBlocks(node)
            ?: error("contiguous blocks missing for ${node.fullPath}")
        val parts = blocks.map { block ->
            CosBlockPart(
                startPos = block.startPos,
                size = block.size,
                sha256 = block.sha256,
                url = signBlockUrl(block.sha256, credentials, clientConfig),
            )
        }
        val body = CosBlockManifest(size = node.size, blocks = parts).toJsonString()
        val response = context.response
        response.status = 200
        response.setHeader(HEADER_BLOCK_MANIFEST, "1")
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.use { it.write(body) }
        logger.info(
            "Write block manifest of ${parts.size} blocks for node[${node.fullPath}] " +
                "to cos[${credentials.key}]",
        )
    }

    private fun loadContiguousBlocks(node: NodeDetail): List<TBlockNode>? {
        val blocks = blockNodeService.listAllBlocks(
            node.projectId,
            node.repoName,
            node.fullPath,
            node.createdDate,
        ).sortedBy { it.startPos }
        if (blocks.isEmpty()) {
            return null
        }
        var cursor = 0L
        for (block in blocks) {
            if (block.startPos != cursor || block.size <= 0L) {
                return null
            }
            cursor += block.size
        }
        return if (cursor == node.size) blocks else null
    }

    private fun signBlockUrl(
        sha256: String,
        credentials: InnerCosCredentials,
        clientConfig: ClientConfig,
    ): String {
        return signCosUrl(GetObjectRequest(sha256), credentials, clientConfig)
    }

    private fun signCosUrl(
        request: GetObjectRequest,
        credentials: InnerCosCredentials,
        clientConfig: ClientConfig,
    ): String {
        val urlencodedSign = request.sign(credentials, clientConfig).urlEncode(true)
        request.url += if (request.parameters.isEmpty()) {
            "?sign=$urlencodedSign"
        } else {
            "&sign=$urlencodedSign"
        }
        return request.url
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

    private fun policyAllowsRedirect(
        context: ArtifactDownloadContext,
        repoSupportRedirectTo: Boolean,
    ): Boolean {
        val request = HttpContextHolder.getRequest()
        val forceByHeader = request.getHeader(HEADER_DOWNLOAD_REDIRECT_TO) == RedirectTo.INNERCOS.name
        if (repoSupportRedirectTo || forceByHeader || storageProperties.redirect.redirectAllDownload) {
            return true
        }
        return isArtifactClient() &&
            storageProperties.redirect.clientDirect.matches(context.projectId, context.repoName)
    }

    private fun isArtifactClient(): Boolean {
        val client = HttpContextHolder.getRequest().getHeader(HEADER_BKREPO_CLIENT)
            ?.substringBefore('/')
            ?.trim()
            .orEmpty()
        return client.equals(BKREPO_CLIENT_NAME, ignoreCase = true)
    }

    private fun isProjectAllowed(projectId: String ): Boolean {
        val blackProjectList = storageProperties.redirect.projectBlackList
        return !blackProjectList.contains(projectId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CosRedirectService::class.java)

        /** 依赖源协议要求的 Content-Type（如 OCI mediaType），优先于按文件名推断 */
        const val ATTR_RESPONSE_CONTENT_TYPE = "cos.redirect.responseContentType"
    }
}

private data class CosBlockManifest(
    val size: Long,
    val blocks: List<CosBlockPart>,
)

private data class CosBlockPart(
    val startPos: Long,
    val size: Long,
    val sha256: String,
    val url: String,
)
