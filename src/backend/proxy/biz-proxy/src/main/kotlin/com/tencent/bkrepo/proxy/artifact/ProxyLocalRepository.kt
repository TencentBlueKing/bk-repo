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

package com.tencent.bkrepo.proxy.artifact

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_MD5
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_SHA256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.constant.BKREPO_META
import com.tencent.bkrepo.generic.constant.BKREPO_META_PREFIX
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.repository.api.proxy.ProxyNodeClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.http.HttpServletRequest

@Component
class ProxyLocalRepository: LocalRepository() {

    @Autowired
    private lateinit var proxyNodeClient: ProxyNodeClient

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val node = proxyNodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            node?.let { downloadIntercept(context, it) }
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()
            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        with(context) {
            val request = buildNodeCreateRequest(this)
            val cancel = AtomicBoolean(false)
            val affectedCount = storageService.store(request.sha256!!, getArtifactFile(), storageCredentials, cancel)
            try {
                val nodeDetail = proxyNodeClient.createNode(request).data!!
                context.response.contentType = MediaTypes.APPLICATION_JSON
                context.response.addHeader(X_CHECKSUM_MD5, getArtifactMd5())
                context.response.addHeader(X_CHECKSUM_SHA256, getArtifactSha256())
                context.response.writer.println(ResponseBuilder.success(nodeDetail).toJsonString())
            } catch (exception: Exception) {
                // 当文件有创建，则删除文件
                delete(affectedCount, cancel, request)
                // 异常往上抛
                throw exception
            }
        }
    }

    private fun ArtifactUploadContext.delete(
        affectedCount: Int,
        cancel: AtomicBoolean,
        request: NodeCreateRequest
    ) {
        if (affectedCount == 1) {
            try {
                cancel.set(true)
                storageService.delete(request.sha256!!, storageCredentials)
            } catch (exception: Exception) {
                logger.error("Failed to delete new created file[${request.sha256}]", exception)
            }
        }
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return super.buildNodeCreateRequest(context).copy(
            expires = HeaderUtils.getLongHeader(HEADER_EXPIRES),
            overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
            nodeMetadata = resolveMetadata(context.request)
        )
    }

    /**
     * 从header中提取metadata
     */
    fun resolveMetadata(request: HttpServletRequest): List<MetadataModel> {
        val metadata = mutableMapOf<String, String>()
        // case insensitive
        val headerNames = request.headerNames
        for (headerName in headerNames) {
            if (headerName.startsWith(BKREPO_META_PREFIX, true)) {
                val key = headerName.substring(BKREPO_META_PREFIX.length).trim().toLowerCase()
                if (key.isNotBlank()) {
                    metadata[key] = HeaderUtils.getUrlDecodedHeader(headerName)!!
                }
            }
        }
        // case sensitive, base64 metadata
        // format X-BKREPO-META: base64(a=1&b=2)
        request.getHeader(BKREPO_META)?.let { metadata.putAll(decodeMetadata(it)) }
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

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyLocalRepository::class.java)
    }
}
