/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.conan.constant.ConanMessageCode
import com.tencent.bkrepo.conan.exception.ConanSearchNotFoundException
import com.tencent.bkrepo.conan.pojo.ConanInfo
import com.tencent.bkrepo.conan.pojo.ConanSearchResult
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.service.ConanSearchService
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildConanFileName
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackagePath
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.toConanFileReference
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ConanSearchServiceImpl : ConanSearchService {

    @Autowired
    lateinit var packageService: PackageService

    @Autowired
    lateinit var commonService: CommonService

    override fun search(
        projectId: String,
        repoName: String,
        pattern: String?,
        ignoreCase: Boolean
    ): ConanSearchResult {
        // TODO 需要对pattern进行校验， -q parameter only allowed with a valid recipe reference, not with a pattern
        val recipes = searchRecipes(projectId, repoName)
        val list = if (pattern.isNullOrEmpty()) {
            recipes
        } else {
            val realPattern = pattern.replace("*", ".*")
            val regex = if (ignoreCase) {
                Regex(realPattern, RegexOption.IGNORE_CASE)
            } else {
                Regex(realPattern)
            }
            recipes.filter { regex.containsMatchIn(it) }
        }
        if (list.isEmpty()) {
            throw ConanSearchNotFoundException(
                ConanMessageCode.CONAN_SEARCH_NOT_FOUND,
                pattern ?: StringPool.EMPTY, "$projectId/$repoName"
            )
        }
        return ConanSearchResult(list)
    }

    override fun searchPackages(pattern: String?, conanArtifactInfo: ConanArtifactInfo): Map<String, ConanInfo> {
        with(conanArtifactInfo) {
            val realRevision = if (revision.isNullOrEmpty()) {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo)
                commonService.getNodeDetail(projectId, repoName, buildPackagePath(conanFileReference))
                commonService.getLastRevision(projectId, repoName, conanFileReference)?.revision ?: return emptyMap()
            } else {
                revision
            }
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, realRevision)
            return commonService.getPackageConanInfo(projectId, repoName, conanFileReference)
        }
    }

    fun searchRecipes(projectId: String, repoName: String): List<String> {
        val result = mutableListOf<String>()
        packageService.listAllPackageName(projectId, repoName).forEach {
            packageService.listAllVersion(projectId, repoName, it, VersionListOption()).forEach { pv ->
                pv.packageMetadata.toConanFileReference()?.apply {
                    result.add(buildConanFileName(this))
                }
            }
        }
        return result.sorted()
    }
}
