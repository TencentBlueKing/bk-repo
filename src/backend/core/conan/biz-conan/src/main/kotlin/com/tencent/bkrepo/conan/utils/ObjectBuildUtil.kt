/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.conan.constant.CHANNEL
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.constant.NAME
import com.tencent.bkrepo.conan.constant.REVISION
import com.tencent.bkrepo.conan.constant.USERNAME
import com.tencent.bkrepo.conan.constant.VERSION
import com.tencent.bkrepo.conan.constant.X_CONAN_SERVER_CAPABILITIES
import com.tencent.bkrepo.conan.controller.ConanCommonController.Companion.capabilities
import com.tencent.bkrepo.conan.pojo.BasicInfo
import com.tencent.bkrepo.conan.pojo.ConanFileReference
import com.tencent.bkrepo.conan.pojo.ConanPackageDeleteRequest
import com.tencent.bkrepo.conan.pojo.ConanPackageUploadRequest
import com.tencent.bkrepo.conan.pojo.ConanRecipeDeleteRequest
import com.tencent.bkrepo.conan.pojo.ConanRecipeUploadRequest
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToPackageReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildConanFileName
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildReferenceWithoutVersion
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getPackageRevisionsFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getRecipeRevisionsFile
import com.tencent.bkrepo.conan.utils.TimeFormatUtil.convertToUtcTime
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import java.time.LocalDateTime
import javax.servlet.http.HttpServletResponse

object ObjectBuildUtil {
    fun buildPackageVersionCreateRequest(
        artifactInfo: ConanArtifactInfo,
        size: Long,
        sourceType: ArtifactChannel? = null,
        userId: String,
    ): PackageVersionCreateRequest {
        with(artifactInfo) {
            // conan key中的name由 实际name+username+channel组成
            val conanFileReference = convertToConanFileReference(artifactInfo)
            val refStr = buildReferenceWithoutVersion(conanFileReference)
            return PackageVersionCreateRequest(
                projectId = projectId,
                repoName = repoName,
                packageName = name,
                packageKey = PackageKeys.ofConan(refStr),
                packageType = PackageType.CONAN,
                versionName = version,
                size = size,
                manifestPath = null,
                artifactPath = ConanPathUtils.generateFullPath(artifactInfo),
                stageTag = null,
                packageMetadata = addPackageMetadata(artifactInfo, sourceType),
                createdBy = userId,
                overwrite = true
            )
        }
    }

    private fun addPackageMetadata(
        artifactInfo: ConanArtifactInfo,
        sourceType: ArtifactChannel? = null,
    ): List<MetadataModel> {
        val result = mutableListOf<MetadataModel>()
        sourceType?.let {
            result.add(MetadataModel(SOURCE_TYPE, sourceType, system = true))
        }
        result.addAll(convertToConanFileReference(artifactInfo).toMetadataList())
        return result
    }

    fun ConanFileReference.toMetadataList(): List<MetadataModel> {
        return listOf(
            MetadataModel(NAME, name, system = true),
            MetadataModel(VERSION, version, system = true),
            MetadataModel(USERNAME, userName, system = true),
            MetadataModel(CHANNEL, channel, system = true),
            MetadataModel(REVISION, revision.orEmpty(), system = true)
        )
    }

    fun List<MetadataModel>.toConanFileReference(): ConanFileReference? {
        val map = this.filter { it.system }.associate { it.key to it.value.toString() }

        return try {
            ConanFileReference(
                name = map[NAME]!!,
                version = map[VERSION]!!,
                userName = map[USERNAME]!!,
                channel = map[CHANNEL]!!,
                revision = map[REVISION],
            )
        } catch (e: Exception) {
            null
        }
    }

    fun buildDownloadResponse(
        response: HttpServletResponse = HttpContextHolder.getResponse(),
        contentType: String = MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
    ) {
        response.addHeader(X_CONAN_SERVER_CAPABILITIES, capabilities.joinToString(","))
        response.addHeader(CONTENT_TYPE, contentType)
    }

    fun buildConanRecipeUpload(
        artifactInfo: ConanArtifactInfo,
        userId: String
    ): ConanRecipeUploadRequest {
        with(artifactInfo) {
            val conanFileReference = convertToConanFileReference(this)
            val revPath = getRecipeRevisionsFile(conanFileReference)
            val refStr = buildConanFileName(conanFileReference)
            return ConanRecipeUploadRequest(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr,
                operator = userId,
                revision = revision ?: DEFAULT_REVISION_V1,
                dateStr = convertToUtcTime(LocalDateTime.now())
            )
        }
    }

    fun buildConanPackageUpload(
        artifactInfo: ConanArtifactInfo,
        userId: String
    ): ConanPackageUploadRequest {
        with(artifactInfo) {
            val packageReference = convertToPackageReference(this)
            val revPath = getRecipeRevisionsFile(packageReference.conRef)
            val refStr = buildConanFileName(packageReference.conRef)
            val pRevPath = getPackageRevisionsFile(packageReference)
            val pRefStr = buildPackageReference(packageReference)
            return ConanPackageUploadRequest(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr,
                operator = userId,
                revision = revision ?: DEFAULT_REVISION_V1,
                dateStr = convertToUtcTime(LocalDateTime.now()),
                pRefStr = pRefStr,
                pRevPath = pRevPath,
                pRevision = pRevision
            )
        }
    }

    fun buildConanRecipeDeleteRequest(
        artifactInfo: ConanArtifactInfo,
        userId: String
    ): ConanRecipeDeleteRequest {
        with(artifactInfo) {
            val packageReference = convertToConanFileReference(this)
            val revPath = getRecipeRevisionsFile(packageReference)
            val refStr = buildConanFileName(packageReference)
            return ConanRecipeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr,
                operator = userId,
                revision = revision ?: DEFAULT_REVISION_V1,
            )
        }
    }

    fun buildConanPackageDeleteRequest(
        artifactInfo: ConanArtifactInfo,
        userId: String,
        packageId: String,
        pRevision: String? = null,
    ): ConanPackageDeleteRequest {
        with(artifactInfo) {
            val packageReference = PackageReference(convertToConanFileReference(artifactInfo), packageId, pRevision)
            val revPath = getRecipeRevisionsFile(packageReference.conRef)
            val refStr = buildConanFileName(packageReference.conRef)
            val pRevPath = getPackageRevisionsFile(packageReference)
            val pRefStr = buildPackageReference(packageReference)
            return ConanPackageDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr,
                operator = userId,
                revision = revision ?: DEFAULT_REVISION_V1,
                pRefStr = pRefStr,
                pRevPath = pRevPath
            )
        }
    }

    fun buildConanPackageDeleteRequest(
        artifactInfo: ConanArtifactInfo,
        userId: String
    ): ConanPackageDeleteRequest {
        with(artifactInfo) {
            val packageReference = convertToPackageReference(this)
            val revPath = getRecipeRevisionsFile(packageReference.conRef)
            val refStr = buildConanFileName(packageReference.conRef)
            val pRevPath = getPackageRevisionsFile(packageReference)
            val pRefStr = buildPackageReference(packageReference)
            return ConanPackageDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                revPath = revPath,
                refStr = refStr,
                operator = userId,
                revision = revision ?: DEFAULT_REVISION_V1,
                pRefStr = pRefStr,
                pRevPath = pRevPath,
                pRevision = pRevision
            )
        }
    }

    fun buildBasicInfo(nodeDetail: NodeDetail, packageVersion: PackageVersion): BasicInfo {
        with(nodeDetail) {
            return BasicInfo(
                version = packageVersion.name,
                fullPath = fullPath,
                size = size,
                sha256 = sha256.orEmpty(),
                md5 = md5.orEmpty(),
                stageTag = packageVersion.stageTag,
                projectId = projectId,
                repoName = repoName,
                downloadCount = packageVersion.downloads,
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate
            )
        }
    }
}
