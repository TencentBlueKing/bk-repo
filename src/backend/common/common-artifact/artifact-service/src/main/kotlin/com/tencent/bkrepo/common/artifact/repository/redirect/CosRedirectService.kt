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

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.endpoint.DefaultEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.innercos.urlEncode
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 当使用对象存储作为后端存储时，支持创建对象的预签名下载URL，并将用户的下载请求重定向到该URL
 */
@Service
@Order(2)
class CosRedirectService(
    private val storageProperties: StorageProperties,
    private val fileLocator: FileLocator
) : DownloadRedirectService {
    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        val storageCredentials = context.storageCredentials
        val isInnerCosStorageCredentials = storageCredentials is InnerCosCredentials ||
                storageProperties.type == StorageType.INNERCOS
        return isInnerCosStorageCredentials && context.redirectTo == RedirectTo.INNERCOS.name
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val credentials = context.storageCredentials ?: storageProperties.defaultStorageCredentials()
        require(credentials is InnerCosCredentials)
        val node = ArtifactContextHolder.getNodeDetail()
            ?: throw NodeNotFoundException(context.artifactInfo.getArtifactFullPath())

        // 创建请求并签名
        val clientConfig = ClientConfig(credentials).apply {
            signExpired = Duration.ofSeconds(DEFAULT_SIGN_EXPIRED_SECOND)
            // 重定向请求不使用北极星解析，直接使用域名
            endpointResolver = DefaultEndpointResolver()
            httpProtocol = HttpProtocol.HTTPS
        }
        val request = GetObjectRequest(node.sha256!!)
        val urlencodedSign = request.sign(credentials, clientConfig).urlEncode(true)
        if (request.parameters.isEmpty()) {
            request.url += "?sign=$urlencodedSign"
        } else {
            request.url += "&sign=$urlencodedSign"
        }

        logger.info("redirect request of download [${node.fullPath}] to cos[${credentials.key}]")
        // 重定向
        context.response.sendRedirect(request.url)
    }

    companion object {
        private const val DEFAULT_SIGN_EXPIRED_SECOND = 3 * 60L
        private val logger = LoggerFactory.getLogger(CosRedirectService::class.java)
    }
}
