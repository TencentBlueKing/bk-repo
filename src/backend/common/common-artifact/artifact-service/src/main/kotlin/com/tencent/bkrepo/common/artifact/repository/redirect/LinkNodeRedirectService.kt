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

import com.tencent.bkrepo.common.api.constant.ACCESS_FROM_API
import com.tencent.bkrepo.common.api.constant.HEADER_ACCESS_FROM
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_FULL_PATH
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_PROJECT
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_REPO
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder.RepositoryId
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service

/**
 * 软链接节点下载重定向
 */
@Service
@Order(2)
class LinkNodeRedirectService(
    private val storageProperties: StorageProperties
) : DownloadRedirectService {

    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        return storageProperties.redirect.enabled
                && isLinkNode(ArtifactContextHolder.getNodeDetail(context.artifactInfo))
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)!!
        val targetProjectId = node.metadata[METADATA_KEY_LINK_PROJECT] as String
        val targetRepoName = node.metadata[METADATA_KEY_LINK_REPO] as String
        val targetFullPath = node.metadata[METADATA_KEY_LINK_FULL_PATH] as String

        // 获取存储所在区域制品库集群url
        val targetRepo = ArtifactContextHolder.getRepoDetail(RepositoryId(targetProjectId, targetRepoName))
        val storageKey = targetRepo.storageCredentials?.key ?: "default"
        val storageHost = storageProperties.redirect.storageHosts[storageKey]
        val redirectUrlBuilder = StringBuilder()
        if (!storageHost.isNullOrEmpty()) {
            context.response.setHeader(HttpHeaders.CONNECTION, "close")
            redirectUrlBuilder.append(storageHost)
        }

        // 判断请求来源是浏览器还是API调用
        if (context.request.getHeader(HEADER_ACCESS_FROM) != ACCESS_FROM_API) {
            redirectUrlBuilder.append("/web")
        }

        // 设置重定向地址
        redirectUrlBuilder.append("/generic/$targetProjectId/$targetRepoName/$targetFullPath")
        context.response.setHeader(HttpHeaders.LOCATION, redirectUrlBuilder.toString())
        context.response.status = HttpStatus.MOVED_PERMANENTLY.value
    }

    private fun isLinkNode(node: NodeDetail?): Boolean {
        val projectId = node?.metadata?.get(METADATA_KEY_LINK_PROJECT) as? String
        val repo = node?.metadata?.get(METADATA_KEY_LINK_REPO) as? String
        val fullPath = node?.metadata?.get(METADATA_KEY_LINK_FULL_PATH) as? String
        return node != null && !projectId.isNullOrEmpty() && !repo.isNullOrEmpty() && !fullPath.isNullOrEmpty()
    }
}
