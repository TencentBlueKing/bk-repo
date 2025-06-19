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

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_MODEL
import com.tencent.bkrepo.huggingface.constants.REVISION_KEY
import com.tencent.bkrepo.huggingface.exception.HfRepoNotFoundException
import com.tencent.bkrepo.huggingface.pojo.DatasetInfo
import com.tencent.bkrepo.huggingface.pojo.ModelInfo
import com.tencent.bkrepo.huggingface.pojo.RepoSibling
import com.tencent.bkrepo.huggingface.service.HfCommonService
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.stereotype.Component

@Component
class HuggingfaceLocalRepository(
    private val hfCommonService: HfCommonService,
) : LocalRepository() {

    override fun onUpload(context: ArtifactUploadContext) {
        with(context) {
            val nodeCreateRequest = buildLfsNodeCreateRequest(this)
            storageManager.storeArtifactFile(nodeCreateRequest, getArtifactFile(), storageCredentials)
        }
    }

    private fun buildLfsNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return buildNodeCreateRequest(context).copy(fullPath = context.artifactInfo.getArtifactName())
    }

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        packageVersion(context)?.let { downloadIntercept(context, it) }
        val artifactInfo = context.artifactInfo
        if (artifactInfo is HuggingfaceArtifactInfo) {
            transferRevision(artifactInfo)
        }
    }

    private fun transferRevision(artifactInfo: HuggingfaceArtifactInfo) {
        with(artifactInfo) {
            if (artifactInfo.getRevision().isNullOrEmpty() || artifactInfo.getRevision() == "main") {
                val packageKey = PackageKeys.ofHuggingface(type ?: REPO_TYPE_MODEL, getRepoId())
                val packageSummary = packageService.findPackageByKey(projectId, repoName, packageKey)
                    ?: throw HfRepoNotFoundException(getRepoId())
                HttpContextHolder.getRequest().setAttribute(REVISION_KEY, packageSummary.latest)
            }
        }
    }

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return super.buildNodeCreateRequest(context).copy(overwrite = true)
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val artifactInfo = context.artifactInfo as HuggingfaceArtifactInfo
        with(artifactInfo) {
            val packageKey = PackageKeys.ofHuggingface(type.toString(), getRepoId())

            val packageSummary = packageService.findPackageByKey(projectId, repoName, packageKey)
                ?: throw HfRepoNotFoundException(getRepoId())
            transferRevision(artifactInfo)
            val packageVersion = packageService.findVersionByName(projectId, repoName, packageKey, getRevision()!!)
            val siblings = if (packageVersion == null) {
                emptyList()
            } else {
                val basePath = "/${getRepoId()}/resolve/${packageVersion.name}/"
                val nodes = nodeService.listNode(
                    ArtifactInfo(projectId, repoName, basePath),
                    NodeListOption(includeFolder = false, deep = true)
                )
                convert(nodes, basePath)
            }
            return if (type == REPO_TYPE_MODEL) {
                ModelInfo(
                    id = packageSummary.name,
                    author = packageSummary.createdBy,
                    sha = packageVersion?.name.orEmpty(),
                    createdAt = packageSummary.createdDate,
                    lastModified = packageSummary.lastModifiedDate,
                    private = false,
                    disabled = false,
                    downloads = packageSummary.downloads.toInt(),
                    downloadsAllTime = packageSummary.downloads.toInt(),
                    gated = "auto",
                    likes = 0,
                    modelIndex = null,
                    config = null,
                    trendingScore = 0,
                    spaces = null,
                    securityRepoStatus = null,
                    siblings = siblings
                )
            } else {
                DatasetInfo(
                    _id = packageSummary.id,
                    id = packageSummary.name,
                    author = packageSummary.createdBy,
                    sha = packageVersion?.name.orEmpty(),
                    lastModified = packageSummary.lastModifiedDate,
                    private = false,
                    gated = false,
                    disabled = false,
                    tags = emptyList(),
                    citation = null,
                    description = "",
                    downloads = 0,
                    likes = 0,
                    cardData = null,
                    siblings = siblings,
                    createdAt = packageSummary.createdDate,
                    usedStorage = 0
                )
            }
        }
    }

    override fun buildDownloadRecord(context: ArtifactDownloadContext, artifactResource: ArtifactResource) =
        hfCommonService.buildDownloadRecord(context)

    private fun packageVersion(context: ArtifactContext) =
        hfCommonService.getPackageVersionByArtifactInfo(context.artifactInfo as HuggingfaceArtifactInfo)

    private fun convert(nodes: List<NodeInfo>, basePath: String): List<RepoSibling> {
        return nodes.map { RepoSibling(
            rfilename = it.fullPath.removePrefix(basePath),
            size = it.size,
            blobId = it.sha256,
            lfs = null
        ) }
    }
}
