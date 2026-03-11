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

import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.TagExistedException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.constants.REVISION_MAIN
import com.tencent.bkrepo.huggingface.exception.HfRepoNotFoundException
import com.tencent.bkrepo.huggingface.exception.HfTagExistException
import com.tencent.bkrepo.huggingface.exception.RevisionNotFoundException
import com.tencent.bkrepo.huggingface.pojo.GitRefInfo
import com.tencent.bkrepo.huggingface.pojo.GitRefs
import com.tencent.bkrepo.huggingface.pojo.user.UserTagCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HfTagService(
    private val packageService: PackageService,
) {

    /**
     * 创建 tag
     */
    fun createTag(
        artifactInfo: HuggingfaceArtifactInfo,
        request: UserTagCreateRequest,
    ) {
        with(artifactInfo) {
            val packageKey = PackageKeys.ofHuggingface(type!!, getRepoId())
            var revision = getRevision()
            if (revision.isNullOrEmpty() || revision == REVISION_MAIN) {
                revision = packageService.listVersionPage(
                    projectId = projectId, repoName = repoName, packageKey = packageKey,
                    option = VersionListOption(
                        pageNumber = 1,
                        pageSize = 1,
                        sortProperty = TPackageVersion::createdDate.name,
                        direction = Sort.Direction.DESC
                    )
                ).records.firstOrNull()?.name ?: throw RevisionNotFoundException(REVISION_MAIN)
            }
            try {
                packageService.createTag(projectId, repoName, packageKey, revision, request.tag, request.message)
            } catch (_: PackageNotFoundException) {
                throw HfRepoNotFoundException(getRepoId())
            } catch (_: VersionNotFoundException) {
                throw RevisionNotFoundException(revision)
            } catch (_: TagExistedException) {
                throw HfTagExistException(request.tag)
            }
        }
    }

    /**
     * 删除 tag
     * 从包含该 tag 的 revision 的 tags 列表中移除
     */
    fun deleteTag(
        artifactInfo: HuggingfaceArtifactInfo,
        tag: String
    ) {
        with(artifactInfo) {
            val packageKey = PackageKeys.ofHuggingface(type!!, getRepoId())
            packageService.deleteTag(projectId, repoName, packageKey, tag)
        }
    }

    /**
     * 列出所有 refs
     */
    fun listRefs(
        artifactInfo: HuggingfaceArtifactInfo,
    ): GitRefs {
        with(artifactInfo) {
            val packageKey = PackageKeys.ofHuggingface(type!!, getRepoId())
            val packageSummary = packageService.findPackageByKey(projectId, repoName, packageKey)
                ?: throw HfRepoNotFoundException(getRepoId())
            return GitRefs(
                branches = emptyList(),
                converts = emptyList(),
                tags = packageSummary.versionTag.map { (tag, version) ->
                    GitRefInfo(tag, "refs/tags/$tag", version)
                },
            )

        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HfTagService::class.java)
    }
}

