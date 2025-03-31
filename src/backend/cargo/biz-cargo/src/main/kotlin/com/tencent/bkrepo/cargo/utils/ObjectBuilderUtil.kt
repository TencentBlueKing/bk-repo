/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.cargo.utils

import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.base.CargoMetadata
import com.tencent.bkrepo.cargo.pojo.base.MetaDependency
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageUploadRequest
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageYankRequest
import com.tencent.bkrepo.cargo.pojo.index.CrateIndex
import com.tencent.bkrepo.cargo.pojo.index.IndexDependency
import com.tencent.bkrepo.cargo.pojo.json.CrateJsonData
import com.tencent.bkrepo.cargo.pojo.json.JsonDataDependency
import com.tencent.bkrepo.cargo.pojo.user.BasicInfo
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest

object ObjectBuilderUtil {

    private val keys: Array<String> = arrayOf("deps", "readme_file", "readme", "license_file")

    fun buildCrateIndexData(metadata: CargoMetadata, cksum: String): CrateIndex {
        with(metadata) {
            return CrateIndex(
                name = name,
                vers = vers,
                features = features,
                cksum = cksum,
                yanked = false,
                deps = deps.map { convert2IndexDependency(it) }
            )
        }
    }

    fun buildCrateJsonData(metadata: CargoMetadata): CrateJsonData {
        with(metadata) {
            return CrateJsonData(
                features = features,
                deps = deps.map { convert2JsonDataDependency(it) }
            )
        }
    }

    fun buildPackageVersionCreateRequest(
        userId: String,
        projectId: String,
        repoName: String,
        cargoMetadata: CargoMetadata,
        size: Long,
        fullPath: String,
        metadataList: List<MetadataModel>
    ): PackageVersionCreateRequest {
        return PackageVersionCreateRequest(
            projectId = projectId,
            repoName = repoName,
            packageName = cargoMetadata.name,
            packageKey = PackageKeys.ofCargo(cargoMetadata.name),
            packageType = PackageType.CARGO,
            packageDescription = cargoMetadata.description,
            versionName = cargoMetadata.vers,
            size = size,
            manifestPath = null,
            artifactPath = fullPath,
            stageTag = null,
            packageMetadata = metadataList,
            overwrite = true,
            createdBy = userId
        )
    }

    fun buildNodeCreateRequest(
        artifactFile: ArtifactFile,
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeCreateRequest {
        val nodeCreateRequest = NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            overwrite = true,
            operator = operator
        )
        return nodeCreateRequest
    }

    fun buildCargoUploadRequest(
        context: ArtifactUploadContext,
        crateIndex: CrateIndex
    ): CargoPackageUploadRequest {
        return CargoPackageUploadRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            name = crateIndex.name,
            version = crateIndex.vers,
            userId = context.userId,
            crateIndex = crateIndex
        )
    }

    fun buildCargoPackageYankRequest(cargoArtifactInfo: CargoArtifactInfo, yanked: Boolean): CargoPackageYankRequest {
        return CargoPackageYankRequest(
            projectId = cargoArtifactInfo.projectId,
            repoName = cargoArtifactInfo.repoName,
            name = cargoArtifactInfo.crateName!!,
            version = cargoArtifactInfo.crateVersion!!,
            userId = SecurityUtils.getUserId(),
            yanked = yanked
        )
    }

    fun convertToMetadata(cargoMetadata: CargoMetadata, sourceType: ArtifactChannel? = null): List<MetadataModel> {
        val mutableMap: MutableList<MetadataModel> = convertToMap(cargoMetadata).map {
            MetadataModel(key = it.key, value = it.value)
        } as MutableList<MetadataModel>
        sourceType?.let {
            mutableMap.add(MetadataModel(SOURCE_TYPE, sourceType))
        }
        return mutableMap
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

    private fun convertToMap(cargoMetadata: CargoMetadata): Map<String, Any> {
        return cargoMetadata.toJsonString().readJsonString<Map<String, Any>>().filter { it.value != null }.minus(keys)
    }

    private fun convert2JsonDataDependency(dependency: MetaDependency): JsonDataDependency {
        with(dependency) {
            return JsonDataDependency(
                name = name,
                versionReq = versionReq,
                optional = optional,
                registry = registry,
                defaultFeatures = defaultFeatures,
                features = features,
                kind = kind,
            )
        }
    }

    private fun convert2IndexDependency(dependency: MetaDependency): IndexDependency {
        with(dependency) {
            return IndexDependency(
                name = name,
                req = versionReq,
                optional = optional,
                registry = registry,
                defaultFeatures = defaultFeatures,
                features = features,
                kind = kind,
                target = target
            )
        }
    }

}
