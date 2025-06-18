/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.artifact

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.constants.COMMIT_ID_HEADER
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_DATASET
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_MODEL
import com.tencent.bkrepo.huggingface.constants.REVISION_KEY
import com.tencent.bkrepo.huggingface.service.HfCommonService
import com.tencent.bkrepo.huggingface.util.HfApi
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import okhttp3.Headers
import org.springframework.stereotype.Component

@Component
class HuggingfaceRemoteRepository(
    private val hfCommonService: HfCommonService,
) : RemoteRepository() {

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        val artifactInfo = context.artifactInfo
        if (artifactInfo is HuggingfaceArtifactInfo) {
            if (artifactInfo.getRevision().isNullOrEmpty() || artifactInfo.getRevision() == "main") {
                transferRevision(context)
            }
        }
    }

    private fun transferRevision(context: ArtifactDownloadContext) {
        val configuration = context.getRemoteConfiguration()
        val response = HfApi.head(
            endpoint = configuration.url,
            token = configuration.credentials.password.orEmpty(),
            artifactUri = context.artifactInfo.getArtifactFullPath()
        )
        val commitId = response.headers[COMMIT_ID_HEADER.lowercase()]
        commitId?.let {
            HttpContextHolder.getRequest().setAttribute(REVISION_KEY, it)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            val configuration = context.getRemoteConfiguration()
            val response = HfApi.download(
                endpoint = configuration.url,
                token = configuration.credentials.password.orEmpty(),
                artifactUri = context.artifactInfo.getArtifactFullPath()
            )
            return onDownloadResponse(context, response, true, false)
        }
    }

    override fun addHeadersOfNode(headers: Headers) {
        super.addHeadersOfNode(headers)
        val response = HttpContextHolder.getResponse()
        headers[COMMIT_ID_HEADER.lowercase()]?.let { response.setHeader(COMMIT_ID_HEADER, it) }
    }


    override fun query(context: ArtifactQueryContext): Any? {
        val configuration = context.getRemoteConfiguration()
        val artifactInfo = context.artifactInfo as HuggingfaceArtifactInfo
        return when (artifactInfo.type) {
            REPO_TYPE_MODEL -> {
                val modelInfo = HfApi.modelInfo(
                    endpoint = configuration.url,
                    token = configuration.credentials.password.orEmpty(),
                    repoId = artifactInfo.getRepoId()
                )
                val packageVersionCreateRequest = PackageVersionCreateRequest(
                    projectId = context.projectId,
                    repoName = context.repoName,
                    packageName = artifactInfo.getRepoId(),
                    packageKey = PackageKeys.ofHuggingface("model", artifactInfo.getRepoId()),
                    packageType = PackageType.HUGGINGFACE,
                    packageDescription = null,
                    versionName = modelInfo.sha,
                    size = 0,
                    artifactPath = "${artifactInfo.getRepoId()}/resolve/${modelInfo.sha}/",
                    createdBy = SecurityUtils.getUserId(),
                    overwrite = true
                )
                packageService.createPackageVersion(packageVersionCreateRequest)
                modelInfo
            }
            REPO_TYPE_DATASET -> {
                val datasetInfo = HfApi.datasetInfo(
                    endpoint = configuration.url,
                    token = configuration.credentials.password.orEmpty(),
                    repoId = artifactInfo.getRepoId()
                )
                val packageVersionCreateRequest = PackageVersionCreateRequest(
                    projectId = context.projectId,
                    repoName = context.repoName,
                    packageName = artifactInfo.getRepoId(),
                    packageKey = PackageKeys.ofHuggingface("model", artifactInfo.getRepoId()),
                    packageType = PackageType.HUGGINGFACE,
                    packageDescription = null,
                    versionName = datasetInfo.sha,
                    size = 0,
                    artifactPath = "${artifactInfo.getRepoId()}/resolve/${datasetInfo.sha}/",
                    createdBy = SecurityUtils.getUserId(),
                    overwrite = true
                )
                packageService.createPackageVersion(packageVersionCreateRequest)
            }
            else -> ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "type:${artifactInfo.type}")
        }
    }

    override fun buildDownloadRecord(context: ArtifactDownloadContext, artifactResource: ArtifactResource) =
        hfCommonService.buildDownloadRecord(context)

}
