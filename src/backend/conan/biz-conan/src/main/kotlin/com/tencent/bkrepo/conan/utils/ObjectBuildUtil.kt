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

package com.tencent.bkrepo.conan.utils

import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_TYPE
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.conan.constant.CONAN_INFOS
import com.tencent.bkrepo.conan.constant.X_CONAN_SERVER_CAPABILITIES
import com.tencent.bkrepo.conan.controller.ConanCommonController.Companion.capabilities
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.service.impl.ConanServiceImpl.Companion.convertToConanFileReference
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest
import javax.servlet.http.HttpServletResponse

object ObjectBuildUtil {
    fun buildPackageVersionCreateRequest(
        artifactInfo: ConanArtifactInfo,
        size: Long,
        sourceType: ArtifactChannel? = null,
        userId: String,
    ): PackageVersionCreateRequest {
        with(artifactInfo) {
            return PackageVersionCreateRequest(
                projectId = projectId,
                repoName = repoName,
                packageName = name,
                packageKey = PackageKeys.ofConan(name),
                packageType = PackageType.CONAN,
                versionName = version,
                size = size,
                manifestPath = null,
                artifactPath = getArtifactFullPath(),
                stageTag = null,
                packageMetadata = addPackageMetadata(artifactInfo, sourceType),
                createdBy = userId
            )
        }
    }

    fun buildPackageVersionUpdateRequest(
        artifactInfo: ConanArtifactInfo,
        size: Long,
        sourceType: ArtifactChannel? = null,
        packageMetadata: List<MetadataModel>? = null
    ): PackageVersionUpdateRequest {
        with(artifactInfo) {
            return PackageVersionUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = PackageKeys.ofConan(name),
                versionName = version,
                size = size,
                manifestPath = null,
                artifactPath = getArtifactFullPath(),
                stageTag = null,
                tags = null,
                packageMetadata = addPackageMetadata(artifactInfo, sourceType, packageMetadata)
            )
        }
    }

    private fun addPackageMetadata(
        artifactInfo: ConanArtifactInfo,
        sourceType: ArtifactChannel? = null,
        packageMetadata: List<MetadataModel>? = null
    ): List<MetadataModel> {
        val result = mutableListOf<MetadataModel>()
        sourceType?.let {
            result.add(MetadataModel(SOURCE_TYPE, sourceType))
        }
        val oldConInfo = packageMetadata?.filter { it.key == CONAN_INFOS }?.first()?.value
        val conanInfo = oldConInfo?.apply { mutableListOf(this).add(convertToConanFileReference(artifactInfo)) }
            ?: listOf(convertToConanFileReference(artifactInfo))
        result.add(MetadataModel(CONAN_INFOS, conanInfo))
        return result
    }

    fun buildPackageUpdateRequest(
        artifactInfo: ConanArtifactInfo
    ): PackageUpdateRequest {
        with(artifactInfo) {
            return PackageUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                name = name,
                packageKey = PackageKeys.ofConan(name),
                versionTag = null,
                extension = mapOf("appVersion" to version)
            )
        }
    }

    fun buildDownloadResponse(
        response: HttpServletResponse = HttpContextHolder.getResponse(),
        contentType: String = MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
    ) {
        response.addHeader(X_CONAN_SERVER_CAPABILITIES, capabilities.joinToString(","))
        response.addHeader(CONTENT_TYPE, contentType)
    }
}