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

package com.tencent.bkrepo.huggingface.service

import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.config.HuggingfaceProperties
import com.tencent.bkrepo.huggingface.pojo.BasicInfo
import com.tencent.bkrepo.huggingface.pojo.HfDomainInfo
import com.tencent.bkrepo.huggingface.pojo.HfVersionInfo
import com.tencent.bkrepo.huggingface.pojo.RepoDeleteRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class HfExtService(
    private val huggingfaceProperties: HuggingfaceProperties,
    private val hfRepoService: HfRepoService,
    private val packageService: PackageService,
    private val nodeService: NodeService,
) {

    fun getVersionDetail(userId: String, artifactInfo: ArtifactInfo): HfVersionInfo {
        with(artifactInfo as HuggingfaceArtifactInfo) {
            val version = getArtifactVersion()!!
            val packageVersion = packageService.findVersionByName(projectId, repoName, getPackageKey(), version)
                ?: throw VersionNotFoundException(version)
            val basic = BasicInfo(
                version = version,
                fullPath = packageVersion.contentPath!!,
                size = nodeService.computeSize(
                    ArtifactInfo(projectId, repoName, PathUtils.toFullPath(getArtifactFullPath()))
                ).size,
                sha256 = "",
                md5 = "",
                stageTag = packageVersion.stageTag,
                projectId = projectId,
                repoName = repoName,
                downloadCount = packageVersion.downloads,
                createdBy = packageVersion.createdBy,
                createdDate = packageVersion.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = packageVersion.lastModifiedBy,
                lastModifiedDate = packageVersion.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
            )
            return HfVersionInfo(basic, packageVersion.packageMetadata)
        }
    }

    fun deletePackage(userId: String, artifactInfo: ArtifactInfo) = delete(artifactInfo)

    fun deleteVersion(userId: String, artifactInfo: ArtifactInfo) = delete(artifactInfo)

    fun getRegistryDomain(): HfDomainInfo {
        return HfDomainInfo(UrlFormatter.formatHost(huggingfaceProperties.domain))
    }

    private fun delete(artifactInfo: ArtifactInfo) {
        with(artifactInfo as HuggingfaceArtifactInfo) {
            val repoDeleteRequest = RepoDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                repoId = getRepoId(),
                type = type!!,
                revision = getRevision(),
            )
            hfRepoService.delete(repoDeleteRequest)
        }
        logger.info(
            "user[${SecurityUtils.getUserId()}] delete " +
                    "[${artifactInfo.getRepoId()}/${artifactInfo.getRevision().orEmpty()}] successfully"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HfExtService::class.java)
    }
}
