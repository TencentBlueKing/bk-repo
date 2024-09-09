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

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.conan.constant.ConanMessageCode
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.exception.ConanFileNotFoundException
import com.tencent.bkrepo.conan.exception.ConanRecipeNotFoundException
import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.RevisionInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.service.ConanService
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToPackageReference
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackagePath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageReference
import com.tencent.bkrepo.conan.utils.PathUtils.buildReference
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ConanServiceImpl : ConanService {

    @Autowired
    lateinit var commonService: CommonService

    override fun getConanFileDownloadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        subFileset: List<String>
    ): Map<String, String> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo)
            val urls = try {
                commonService.getDownloadConanFileUrls(
                    projectId, repoName, conanFileReference, subFileset
                )
            } catch (ignore: NodeNotFoundException) {
                emptyMap()
            }
            if (urls.isEmpty())
                throw ConanRecipeNotFoundException(
                    ConanMessageCode.CONAN_RECIPE_NOT_FOUND, buildReference(conanFileReference)
                )
            return urls
        }
    }

    override fun getPackageDownloadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        subFileset: List<String>
    ): Map<String, String> {
        with(conanArtifactInfo) {
            val packageReference = convertToPackageReference(conanArtifactInfo)
            val urls = commonService.getPackageDownloadUrls(
                projectId, repoName, packageReference, subFileset
            )
            if (urls.isEmpty())
                throw ConanRecipeNotFoundException(
                    ConanMessageCode.CONAN_RECIPE_NOT_FOUND, "${buildReference(packageReference.conRef)}/$packageId"
                )
            return urls
        }
    }

    override fun getRecipeSnapshot(conanArtifactInfo: ConanArtifactInfo): Map<String, String> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo)
            return commonService.getRecipeSnapshot(projectId, repoName, conanFileReference)
        }
    }

    override fun getPackageSnapshot(conanArtifactInfo: ConanArtifactInfo): Map<String, String> {
        with(conanArtifactInfo) {
            val packageReference = convertToPackageReference(conanArtifactInfo)
            return commonService.getPackageSnapshot(projectId, repoName, packageReference)
        }
    }

    override fun getConanFileUploadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        fileSizes: Map<String, String>
    ): Map<String, String> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, DEFAULT_REVISION_V1)
            return commonService.getConanFileUploadUrls(projectId, repoName, conanFileReference, fileSizes)
        }
    }

    override fun getPackageUploadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        fileSizes: Map<String, String>
    ): Map<String, String> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, DEFAULT_REVISION_V1)
            val packageReference = PackageReference(conanFileReference, packageId!!, DEFAULT_REVISION_V1)
            return commonService.getPackageUploadUrls(projectId, repoName, packageReference, fileSizes)
        }
    }

    override fun getPackageRevisionFiles(conanArtifactInfo: ConanArtifactInfo): Map<String, Map<String, String>> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
            val packageReference = PackageReference(conanFileReference, packageId!!, pRevision)
            return commonService.getPackageFiles(projectId, repoName, packageReference)
        }
    }

    override fun getRecipeRevisionFiles(conanArtifactInfo: ConanArtifactInfo): Map<String, Map<String, String>> {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
            return commonService.getRecipeFiles(projectId, repoName, conanFileReference)
        }
    }

    override fun getRecipeRevisions(conanArtifactInfo: ConanArtifactInfo): IndexInfo {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo)
            commonService.checkNodeExist(projectId, repoName, buildReference(conanFileReference))
            return commonService.getRecipeRevisions(projectId, repoName, conanFileReference)
        }
    }

    override fun getRecipeLatestRevision(conanArtifactInfo: ConanArtifactInfo): RevisionInfo {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo)
            commonService.checkNodeExist(projectId, repoName, buildPackagePath(conanFileReference))
            return commonService.getLastRevision(projectId, repoName, conanFileReference)
                ?: throw ConanFileNotFoundException(
                    ConanMessageCode.CONAN_FILE_NOT_FOUND, buildReference(conanFileReference), getRepoIdentify()
                )
        }
    }

    override fun getPackageRevisions(conanArtifactInfo: ConanArtifactInfo): IndexInfo {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
            val packageReference = PackageReference(conanFileReference, packageId!!, pRevision)
            return commonService.getPackageRevisions(projectId, repoName, packageReference)
        }
    }

    override fun getPackageLatestRevision(conanArtifactInfo: ConanArtifactInfo): RevisionInfo {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
            val packageReference = PackageReference(conanFileReference, packageId!!, pRevision)
            return commonService.getLastPackageRevision(projectId, repoName, packageReference)
                ?: throw ConanFileNotFoundException(
                    ConanMessageCode.CONAN_FILE_NOT_FOUND, buildPackageReference(packageReference), getRepoIdentify()
                )
        }
    }
}
