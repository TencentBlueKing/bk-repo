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
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.conan.constant.ConanMessageCode
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.exception.ConanFileNotFoundException
import com.tencent.bkrepo.conan.listener.event.ConanPackageDeleteEvent
import com.tencent.bkrepo.conan.listener.event.ConanRecipeDeleteEvent
import com.tencent.bkrepo.conan.pojo.ConanDomainInfo
import com.tencent.bkrepo.conan.pojo.PackageVersionInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.service.ConanDeleteService
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToPackageReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildExportFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageIdFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackagePath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildPackageRevisionFolderPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildRevisionPath
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getPackageRevisionsFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.joinString
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildBasicInfo
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.toConanFileReference
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ConanDeleteServiceImpl : ConanDeleteService {

    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var packageClient: PackageClient

    @Autowired
    lateinit var commonService: CommonService

    override fun removeConanFile(conanArtifactInfo: ConanArtifactInfo) {
        with(conanArtifactInfo) {
            if (revision.isNullOrEmpty()) {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo)
                val rootPath = PathUtils.normalizeFullPath(buildPackagePath(conanFileReference))
                logger.info("Will delete folder $rootPath in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                val refStr = ConanPathUtils.buildReferenceWithoutVersion(conanFileReference)
                val packageKey = PackageKeys.ofConan(refStr)
                packageClient.deleteVersion(projectId, repoName, packageKey, version)
            } else {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
                val rootPath = PathUtils.normalizeFullPath(buildRevisionPath(conanFileReference))
                logger.info("Will delete folder $rootPath in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                publishEvent(
                    ConanRecipeDeleteEvent(
                        ObjectBuildUtil.buildConanRecipeDeleteRequest(this, SecurityUtils.getUserId())
                    )
                )
            }
        }
    }

    override fun removePackages(conanArtifactInfo: ConanArtifactInfo, revisionId: String, packageIds: List<String>) {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, revisionId)
            if (packageIds.isEmpty()) {
                val path = PathUtils.normalizeFullPath(buildPackageFolderPath(conanFileReference))
                logger.info("Will delete folder $path in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                return
            }
            val revPath = PathUtils.normalizeFullPath(getPackageRevisionsFile(conanFileReference))
            val storedPackageIds = commonService.getPackageIdList(projectId, repoName, revPath)
            for (packageId in packageIds) {
                if (!storedPackageIds.contains(packageId)) {
                    continue
                }
                val path = PathUtils.normalizeFullPath(buildPackageIdFolderPath(conanFileReference, packageId))
                logger.info("Will delete folder $path in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            }
        }
    }

    override fun removePackage(conanArtifactInfo: ConanArtifactInfo) {
        with(conanArtifactInfo) {
            if (pRevision.isNullOrEmpty()) {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo)
                val rootPath = PathUtils.normalizeFullPath(buildPackageIdFolderPath(conanFileReference, packageId!!))
                logger.info("Will delete folder $rootPath in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            } else {
                val packageReference = convertToPackageReference(conanArtifactInfo)
                val rootPath = PathUtils.normalizeFullPath(buildPackageRevisionFolderPath(packageReference))
                logger.info("Will delete folder $rootPath in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                publishEvent(
                    ConanPackageDeleteEvent(
                        ObjectBuildUtil.buildConanPackageDeleteRequest(this, SecurityUtils.getUserId())
                    )
                )
            }
        }
    }

    override fun removeRecipeFiles(conanArtifactInfo: ConanArtifactInfo, files: List<String>) {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, DEFAULT_REVISION_V1)
            val rootPath = PathUtils.normalizeFullPath(buildExportFolderPath(conanFileReference))
            var path: String?
            for (file in files) {
                path = joinString(rootPath, file)
                nodeClient.getNodeDetail(projectId, repoName, path).data
                    ?: throw ConanFileNotFoundException(
                        ConanMessageCode.CONAN_FILE_NOT_FOUND, path, "$projectId|$repoName"
                    )
                logger.info("Will delete folder $path in repo $projectId|$repoName")
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                publishEvent(
                    ConanRecipeDeleteEvent(
                        ObjectBuildUtil.buildConanRecipeDeleteRequest(this, SecurityUtils.getUserId())
                    )
                )
            }
        }
    }

    override fun removePackageByKey(conanArtifactInfo: ConanArtifactInfo, packageKey: String) {
        with(conanArtifactInfo) {
            packageClient.listAllVersion(projectId, repoName, packageKey).data.orEmpty().forEach {
                deleteVersion(projectId, repoName, packageKey, it, false)
            }
            packageClient.deletePackage(projectId, repoName, packageKey)
        }
    }

    override fun removePackageVersion(conanArtifactInfo: ConanArtifactInfo, packageKey: String, version: String) {
        with(conanArtifactInfo) {
            val versionDetail = packageClient.findVersionByName(projectId, repoName, packageKey, version).data ?: return
            deleteVersion(projectId, repoName, packageKey, versionDetail)
        }
    }

    override fun getDomain(): ConanDomainInfo {
        return ConanDomainInfo(
            domain = commonService.getDomain()
        )
    }

    override fun detailVersion(
        conanArtifactInfo: ConanArtifactInfo, packageKey: String, version: String
    ): PackageVersionInfo {
        with(conanArtifactInfo) {
            val versionDetail = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
                ?: throw VersionNotFoundException(version)
            if (versionDetail.contentPath.isNullOrEmpty()) throw VersionNotFoundException(version)
            val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, versionDetail.contentPath!!).data ?: run {
                throw NodeNotFoundException(versionDetail.contentPath!!)
            }
            val basicInfo = buildBasicInfo(nodeDetail, versionDetail)
            return PackageVersionInfo(basicInfo, versionDetail.packageMetadata)
        }
    }

    private fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionDetail: PackageVersion,
        deleteVersion: Boolean = true
    ) {
        val conanFileReference = versionDetail.packageMetadata.toConanFileReference() ?: return
        val rootPath = PathUtils.normalizeFullPath(buildPackagePath(conanFileReference))
        logger.info("Will delete folder $rootPath in repo $projectId|$repoName")
        val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
        nodeClient.deleteNode(request)
        if (deleteVersion) {
            packageClient.deleteVersion(projectId, repoName, packageKey, versionDetail.name)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ConanDeleteServiceImpl::class.java)
    }
}
