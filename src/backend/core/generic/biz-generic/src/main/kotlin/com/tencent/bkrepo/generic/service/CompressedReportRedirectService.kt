/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.constant.REPORT
import com.tencent.bkrepo.common.api.constant.ACCESS_FROM_API
import com.tencent.bkrepo.common.api.constant.HEADER_ACCESS_FROM
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.redirect.DownloadRedirectService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.generic.config.GenericProperties
import org.springframework.stereotype.Component

@Component
class CompressedReportRedirectService(
    private val nodeService: NodeService,
    private val genericProperties: GenericProperties
) : DownloadRedirectService {
    override fun shouldRedirect(context: ArtifactDownloadContext): Boolean {
        if (!genericProperties.compressedReport.enabled) {
            return false
        }
        if (context.repoName != REPORT) {
            return false
        }
        // 报告存储路径 /{pipelineId}/{buildId}/{taskId}/xxxx
        if (context.artifactInfo.getArtifactFullPath().split(StringPool.SLASH).size < 4) {
            return false
        }
        val node = ArtifactContextHolder.getNodeDetail(context.artifactInfo)
        val compressedReportFullPath = buildCompressedReportFullPath(context)
        val compressedReportArtifactInfo = ArtifactInfo(context.projectId, context.repoName, compressedReportFullPath)
        val compressedReport = nodeService.getNodeDetail(compressedReportArtifactInfo)
        return node == null && compressedReport != null
    }

    override fun redirect(context: ArtifactDownloadContext) {
        val commonPrefix = PathUtils.getCommonPath(
            context.artifactInfo.getArtifactFullPath(),
            buildCompressedReportFullPath(context)
        )
        val filePath = context.artifactInfo.getArtifactFullPath().removePrefix(commonPrefix)
        val redirectUrlBuilder = StringBuilder()
        redirectUrlBuilder.append(genericProperties.domain.removeSuffix("/generic"))
        // 判断请求来源是浏览器还是API调用
        if (context.request.getHeader(HEADER_ACCESS_FROM) != ACCESS_FROM_API) {
            redirectUrlBuilder.append("/web")
        }
        redirectUrlBuilder.append("/preview/compressed/report/preview${context.request.requestURI}?filePath=$filePath")
        context.request.queryString?.let {
            redirectUrlBuilder.append("&${context.request.queryString}")
        }
        context.response.sendRedirect(redirectUrlBuilder.toString())
    }

    private fun buildCompressedReportFullPath(context: ArtifactDownloadContext): String {
        return context.artifactInfo.getArtifactFullPath().split(StringPool.SLASH)
            .subList(0,4).joinToString(StringPool.SLASH)
            .plus("/${genericProperties.compressedReport.zipFileName}")
    }
}
