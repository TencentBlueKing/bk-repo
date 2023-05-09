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

import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.conan.constant.ConanMessageCode
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.exception.ConanFileNotFoundException
import com.tencent.bkrepo.conan.listener.event.ConanRecipeDeleteEvent
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.service.ConanDeleteService
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToPackageReference
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil
import com.tencent.bkrepo.conan.utils.PathUtils.buildExportFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageIdFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackagePath
import com.tencent.bkrepo.conan.utils.PathUtils.buildPackageRevisionFolderPath
import com.tencent.bkrepo.conan.utils.PathUtils.buildRevisionPath
import com.tencent.bkrepo.conan.utils.PathUtils.getPackageRevisionsFile
import com.tencent.bkrepo.conan.utils.PathUtils.joinString
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
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
                val packageKey = PackageKeys.ofConan(name, userName)
                packageClient.deleteVersion(projectId, repoName, packageKey, version)
                val conanFileReference = convertToConanFileReference(conanArtifactInfo)
                // TODO 路径需要优化
                val rootPath = "/${buildPackagePath(conanFileReference)}"
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            } else {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo, revision)
                val rootPath = "/${buildRevisionPath(conanFileReference)}"
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
                val path = buildPackageFolderPath(conanFileReference)
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
                return
            }
            val revPath = getPackageRevisionsFile(conanFileReference)
            val storedPackageIds = commonService.getPackageIdList(projectId, repoName, revPath)
            for (packageId in packageIds) {
                if (!storedPackageIds.contains(packageId)) {
                    continue
                }
                val path = buildPackageIdFolderPath(conanFileReference, packageId)
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            }
        }
    }

    override fun removePackage(conanArtifactInfo: ConanArtifactInfo) {
        with(conanArtifactInfo) {
            if (pRevision.isNullOrEmpty()) {
                val conanFileReference = convertToConanFileReference(conanArtifactInfo)
                val rootPath = buildPackageIdFolderPath(conanFileReference, packageId!!)
                val request = NodeDeleteRequest(projectId, repoName, rootPath, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            } else {
                val packageReference = convertToPackageReference(conanArtifactInfo)
                val rootPath = buildPackageRevisionFolderPath(packageReference)
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

    override fun removeRecipeFiles(conanArtifactInfo: ConanArtifactInfo, files: List<String>) {
        with(conanArtifactInfo) {
            val conanFileReference = convertToConanFileReference(conanArtifactInfo, DEFAULT_REVISION_V1)
            val rootPath = buildExportFolderPath(conanFileReference)
            var path: String?
            for (file in files) {
                path = joinString(rootPath, file)
                nodeClient.getNodeDetail(projectId, repoName, path).data
                    ?: throw ConanFileNotFoundException(
                        ConanMessageCode.CONAN_FILE_NOT_FOUND, path, "$projectId|$repoName"
                    )
                val request = NodeDeleteRequest(projectId, repoName, path, SecurityUtils.getUserId())
                nodeClient.deleteNode(request)
            }
        }
    }
}
