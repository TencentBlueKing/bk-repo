/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.oci.constant.BLOB_PATH_REFRESHED_KEY
import com.tencent.bkrepo.oci.constant.DIGEST_LIST
import com.tencent.bkrepo.oci.constant.IMAGE_VERSION
import com.tencent.bkrepo.oci.constant.MEDIA_TYPE
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.user.BasicInfo
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest

object ObjectBuildUtils {

    fun buildNodeCreateRequest(
        projectId: String,
        repoName: String,
        artifactFile: ArtifactFile,
        fullPath: String,
        metadata: List<MetadataModel>? = null
    ): NodeCreateRequest {
        return buildNodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            fullPath = fullPath,
            metadata = metadata
        )
    }

    fun buildNodeCreateRequest(
        projectId: String,
        repoName: String,
        size: Long,
        fullPath: String,
        sha256: String,
        md5: String,
        metadata: List<MetadataModel>? = null,
        userId: String = SecurityUtils.getUserId()
    ): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = fullPath,
            size = size,
            sha256 = sha256,
            md5 = md5,
            operator = userId,
            overwrite = true,
            nodeMetadata = metadata
        )
    }

    fun buildMetadata(
        mediaType: String,
        version: String?,
        digestList: List<String>? = null,
        sourceType: ArtifactChannel? = null
    ): MutableMap<String, Any> {
        return mutableMapOf<String, Any>(
            MEDIA_TYPE to mediaType,
            BLOB_PATH_REFRESHED_KEY to true
        ).apply {
            version?.let { this.put(IMAGE_VERSION, version) }
            digestList?.let { this.put(DIGEST_LIST, digestList) }
            sourceType?.let { this.put(SOURCE_TYPE, sourceType) }
        }
    }

    fun buildMetadataSaveRequest(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String,
        metadata: Map<String, Any>? = null
        ): MetadataSaveRequest {
        val metadataModels = metadata?.map { MetadataModel(key = it.key, value = it.value, system = true) }
        return MetadataSaveRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            nodeMetadata = metadataModels,
            operator = userId
        )
    }

    fun buildPackageMetadataSaveRequest(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        userId: String,
        metadata: Map<String, Any>? = null
    ): PackageMetadataSaveRequest {
        val metadataModels = metadata?.map { MetadataModel(key = it.key, value = it.value, system = true) }
        return PackageMetadataSaveRequest(
            projectId = projectId,
            repoName = repoName,
            packageKey = packageKey,
            version = version,
            versionMetadata = metadataModels,
            operator = userId
        )
    }

    fun buildPackageVersionCreateRequest(
        ociArtifactInfo: OciManifestArtifactInfo,
        packageName: String,
        version: String,
        size: Long,
        manifestPath: String,
        repoType: String,
        userId: String
    ): PackageVersionCreateRequest {
        with(ociArtifactInfo) {
            // 兼容多仓库类型支持
            val packageType = PackageType.valueOf(repoType)
            val packageKey = PackageKeys.ofName(repoType.toLowerCase(), packageName)
            return PackageVersionCreateRequest(
                projectId = projectId,
                repoName = repoName,
                packageName = packageName,
                packageKey = packageKey,
                packageType = packageType,
                versionName = version,
                size = size,
                artifactPath = manifestPath,
                manifestPath = manifestPath,
                overwrite = true,
                createdBy = userId
            )
        }
    }

    fun buildPackageVersionUpdateRequest(
        ociArtifactInfo: OciManifestArtifactInfo,
        version: String,
        size: Long,
        manifestPath: String,
        packageKey: String
    ): PackageVersionUpdateRequest {
        with(ociArtifactInfo) {
            return PackageVersionUpdateRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                versionName = version,
                size = size,
                artifactPath = manifestPath,
                manifestPath = manifestPath
            )
        }
    }

    fun buildPackageUpdateRequest(
        artifactInfo: ArtifactInfo,
        name: String,
        packageKey: String,
        appVersion: String? = null,
        description: String? = null
    ): PackageUpdateRequest {
        return PackageUpdateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            name = name,
            description = description,
            packageKey = packageKey,
            extension = appVersion?.let { mapOf("appVersion" to appVersion) }
        )
    }

    fun buildBasicInfo(nodeDetail: NodeDetail, packageVersion: PackageVersion): BasicInfo {
        with(nodeDetail) {
            return BasicInfo(
                version = packageVersion.name,
                fullPath = fullPath,
                size = packageVersion.size,
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
