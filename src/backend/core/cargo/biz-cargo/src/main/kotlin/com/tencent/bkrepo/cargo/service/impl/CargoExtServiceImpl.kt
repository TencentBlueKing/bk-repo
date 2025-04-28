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

package com.tencent.bkrepo.cargo.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.cargo.config.CargoProperties
import com.tencent.bkrepo.cargo.constants.CargoMessageCode
import com.tencent.bkrepo.cargo.constants.YANKED
import com.tencent.bkrepo.cargo.exception.CargoFileNotFoundException
import com.tencent.bkrepo.cargo.pojo.CargoDomainInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoDeleteArtifactInfo
import com.tencent.bkrepo.cargo.pojo.base.CargoMetadata
import com.tencent.bkrepo.cargo.pojo.index.CrateIndex
import com.tencent.bkrepo.cargo.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.cargo.service.CargoExtService
import com.tencent.bkrepo.cargo.utils.CargoUtils
import com.tencent.bkrepo.cargo.utils.CargoUtils.getCargoIndexFullPath
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.buildNodeCreateRequest
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil.convert2IndexDependency
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CargoExtServiceImpl(
    private val cargoProperties: CargoProperties,
    private val commonService: CommonService,
) : CargoExtService {

    override fun detailVersion(
        userId: String,
        artifactInfo: CargoArtifactInfo,
        packageKey: String,
        version: String
    ): PackageVersionInfo {
        with(artifactInfo) {
            val name = PackageKeys.resolveCargo(packageKey)
            val fullPath = CargoUtils.getCargoFileFullPath(name, version)
            val nodeDetail = commonService.getNodeDetail(projectId, repoName, fullPath) ?: run {
                logger.warn("Cloud not find cargo node [$fullPath].")
                throw CargoFileNotFoundException(
                    CargoMessageCode.CARGO_FILE_NOT_FOUND, fullPath, "$projectId|$repoName"
                )
            }
            val packageVersion = commonService.findVersionByName(projectId, repoName, packageKey, version) ?: run {
                logger.warn("Cloud not find cargo package [$packageKey] with version $version.")
                throw PackageNotFoundException(packageKey)
            }
            val basicInfo = ObjectBuilderUtil.buildBasicInfo(nodeDetail, packageVersion)
            return PackageVersionInfo(basicInfo, packageVersion.packageMetadata)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun deletePackage(userId: String, artifactInfo: CargoDeleteArtifactInfo) {
        logger.info("handling delete cargo request: [$artifactInfo]")
        with(artifactInfo) {
            if (!commonService.packageExist(projectId, repoName, packageName)) {
                throw VersionNotFoundException(version)
            }
            val context = ArtifactRemoveContext()
            ArtifactContextHolder.getRepository().remove(context)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun deleteVersion(userId: String, artifactInfo: CargoDeleteArtifactInfo) {
        logger.info("handling delete cargo version request: [$artifactInfo]")
        with(artifactInfo) {
            if (!commonService.packageVersionExist(projectId, repoName, packageName, version)) {
                throw PackageNotFoundException(packageName)
            }
            val context = ArtifactRemoveContext()
            ArtifactContextHolder.getRepository().remove(context)
        }
    }

    override fun getRegistryDomain(): CargoDomainInfo {
        return CargoDomainInfo(UrlFormatter.formatHost(cargoProperties.domain))
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun fixIndex(artifactInfo: CargoArtifactInfo) {
        with(artifactInfo) {
            if (crateName.isNullOrEmpty()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, listOf("crateName"))
            }
            val indexFullPath = getCargoIndexFullPath(crateName)
            commonService.lockAction(projectId, repoName, indexFullPath) {
                regenerateCrateIndex(artifactInfo)
            }
        }
    }


    private fun regenerateCrateIndex(artifactInfo: CargoArtifactInfo) {
        with(artifactInfo) {
            var pageNum = 1
            val crateIndexMap = commonService.getIndexOfCrate(projectId, repoName, crateName!!)
                .associateBy { it.vers }.toMutableMap()
            val existsVersions = mutableListOf<String>()
            while (true) {
                val result = commonService.queryCrateNodesByCrateName(projectId, repoName, crateName, pageNum)
                if (result.records.isEmpty()) break
                for (record in result.records) {
                    fixVersionIndex(artifactInfo, record, crateIndexMap, existsVersions)
                }
                pageNum++
            }
            logger.info("will upload index of crate $crateName in repo $projectId|$repoName")
            removeDeletedVersion(crateIndexMap, existsVersions)
            storeIndex(artifactInfo, crateIndexMap.values.toMutableList())
        }
    }

    private fun fixVersionIndex(
        artifactInfo: CargoArtifactInfo,
        record: Map<String, Any?>,
        crateIndexMap: MutableMap<String, CrateIndex>,
        versionList: MutableList<String>
    ) {
        try {
            val metadata = record[TNode::metadata.name] as? Map<String, Any>
            val version = metadata?.get(CargoMetadata::vers.name) as? String
            val yanked = metadata?.get(YANKED) as? Boolean
            val cksha256 =record[TNode::sha256.name] as? String
            if (version.isNullOrEmpty() || cksha256.isNullOrEmpty()) {
                logger.warn("version or sha256 of crate ${artifactInfo.crateName} is empty")
                return
            }
            versionList.add(version)
            val versionIndex = crateIndexMap[version]

            if (versionIndex == null) {
                crateIndexMap[version] = buildCrateIndex(
                    artifactInfo.projectId, artifactInfo.repoName, artifactInfo.crateName!!, version, yanked, cksha256
                )
            } else {
                if (versionIndex.cksum != cksha256) {
                    crateIndexMap[version] = buildCrateIndex(
                        artifactInfo.projectId, artifactInfo.repoName,
                        artifactInfo.crateName!!, version, yanked, cksha256
                    )
                }
                if (versionIndex.yanked != yanked) {
                    versionIndex.yanked = yanked ?: false
                }
            }
        } catch (ex: Exception) {
            logger.warn(
                "generate indexFile for crate ${artifactInfo.crateName}  with $record in " +
                    "[${artifactInfo.getRepoIdentify()}] failed, ${ex.message}"
            )
        }
    }

    private fun removeDeletedVersion(crateIndexMap: MutableMap<String, CrateIndex>, versionList: MutableList<String>) {
        crateIndexMap.keys.retainAll { versionList.contains(it) }
    }

    private fun storeIndex(artifactInfo: CargoArtifactInfo, crateIndexList: MutableList<CrateIndex>) {
        with(artifactInfo) {
            val indexFullPath = getCargoIndexFullPath(crateName!!)
            val versions = crateIndexList.sortedBy { it.vers }.toMutableList()
            val artifactFile = commonService.buildIndexArtifactFile(
                versions, commonService.getStorageCredentials(projectId, repoName)
            )
            val nodeCreateRequest = buildNodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = indexFullPath,
                operator = SecurityUtils.getUserId(),
                artifactFile = artifactFile
            )
            commonService.uploadIndexOfCrate(artifactFile, nodeCreateRequest)
            logger.info("upload index of crate $crateName in repo $projectId|$repoName success")
        }
    }

    private fun buildCrateIndex(
        projectId: String,
        repoName: String,
        crateName: String,
        version: String,
        yanked: Boolean?,
        cksha256: String
    ): CrateIndex {
        val jsonData = commonService.getJsonOfCrate(projectId, repoName, crateName, version)
        return CrateIndex(
            name = crateName,
            vers = version,
            deps = jsonData?.deps?.map { convert2IndexDependency(it) }.orEmpty(),
            yanked = yanked ?: false,
            cksum = cksha256,
            features = jsonData?.features.orEmpty()
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CargoExtServiceImpl::class.java)
        const val PAGE_SIZE = 1000
    }
}