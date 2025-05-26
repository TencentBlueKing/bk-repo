/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.util.FileNameParser
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.constant.NAME
import com.tencent.bkrepo.repository.constant.PROXY_DOWNLOAD_URL
import com.tencent.bkrepo.common.metadata.constant.VERSION
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.MalformedURLException
import java.net.URL

/**
 * 公共远程仓库
 */
@Component
class CommonRemoteRepository : RemoteRepository() {
    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        logger.info("Will prepare to create remote download url...")
        val type = context.repositoryDetail.type
        // 当前在repository中下载remote类型仓库只支持helm，因为helm将对应chart包信息已经放入packageversion的metadata中
        return if (RepositoryType.HELM != type) {
            super.createRemoteDownloadUrl(context)
        } else {
            buildChartDownloadUrl(context)
        }
    }

    /**
     * 获取缓存节点创建请求
     */
    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val type = context.repositoryDetail.type
        // 当前在repository中下载remote类型仓库只支持helm，因为helm将对应chart包信息已经放入packageversion的metadata中
        return if (RepositoryType.HELM != type) {
            super.buildCacheNodeCreateRequest(context, artifactFile)
        } else {
            val metadata = context.getAttribute<Map<String, Any>>("meta_detail")
            NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.artifactInfo.getArtifactFullPath(),
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                operator = context.userId,
                metadata = metadata,
                nodeMetadata = metadata?.map { MetadataModel(key = it.key, value = it.value) },
                overwrite = true
            )
        }
    }

    override fun onDownloadRedirect(context: ArtifactDownloadContext): Boolean {
        return redirectManager.redirect(context)
    }

    private fun buildChartDownloadUrl(context: ArtifactContext): String {
        val remoteConfiguration = context.getRemoteConfiguration()
        val remoteDomain = remoteConfiguration.url.trimEnd('/')
        val map = FileNameParser.parseNameAndVersionWithRegex(context.artifactInfo.getArtifactFullPath())
        val name = map[NAME].toString()
        val version = map[VERSION].toString()
        val packageVersion = packageService.findVersionByName(
            projectId = context.projectId,
            repoName = context.repoName,
            packageKey = PackageKeys.ofHelm(name),
            versionName = version
        )
        var downloadUrl = CHART_REQUEST_URL.format(
            remoteConfiguration.url,
            context.artifactInfo.getArtifactFullPath()
        )
        if (packageVersion != null) {
            val proxyDownloadUrl = packageVersion.metadata[PROXY_DOWNLOAD_URL]?.toString()
            if (proxyDownloadUrl != null) {
                downloadUrl = if (!proxyDownloadUrl.contains(remoteDomain)) {
                    remoteDomain + StringPool.SLASH + proxyDownloadUrl
                } else {
                    proxyDownloadUrl
                }
            }
        }
        logger.info("remote chart download url is $downloadUrl")
        return downloadUrl
    }

    /**
     * 如果fullPath已经是完整的url，则直接使用，否则进行拼接
     */
    private fun checkUrl(fullPath: String): Boolean {
        return try {
            URL(fullPath)
            true
        } catch (e: MalformedURLException) {
            false
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CommonRemoteRepository::class.java)
        const val CHART_REQUEST_URL = "%s/charts%s"
    }
}
