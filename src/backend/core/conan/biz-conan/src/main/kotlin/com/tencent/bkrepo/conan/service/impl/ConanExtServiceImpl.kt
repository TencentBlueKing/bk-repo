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

package com.tencent.bkrepo.conan.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.conan.config.ConanProperties
import com.tencent.bkrepo.conan.constant.INDEX_JSON
import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.RevisionInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.metadata.ConanMetadataRequest
import com.tencent.bkrepo.conan.pojo.request.IndexRefreshRequest
import com.tencent.bkrepo.conan.service.ConanExtService
import com.tencent.bkrepo.conan.service.ConanMetadataService
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToPackageReference
import com.tencent.bkrepo.conan.utils.ConanPathUtils
import com.tencent.bkrepo.conan.utils.ConanPathUtils.buildConanFileName
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getPackageRevisionsFile
import com.tencent.bkrepo.conan.utils.ConanPathUtils.getRecipeRevisionsFile
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.toConanFileReference
import com.tencent.bkrepo.conan.utils.TimeFormatUtil
import com.tencent.bkrepo.common.metadata.pojo.node.NodeListOption
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageListOption
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageVersion
import com.tencent.bkrepo.common.metadata.pojo.packages.VersionListOption
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ConanExtServiceImpl(
    private val properties: ConanProperties
) : ConanExtService {

    @Autowired
    lateinit var nodeService: NodeService

    @Autowired
    lateinit var packageService: PackageService

    @Autowired
    lateinit var commonService: CommonService

    @Autowired
    lateinit var conanMetadataService: ConanMetadataService

    override fun indexRefreshForRepo(projectId: String, repoName: String) {
        packageSearch(projectId, repoName, true)
    }

    override fun indexRefreshByPackageKey(projectId: String, repoName: String, key: String) {
        versionSearch(projectId, repoName, key, true)
    }

    override fun indexRefreshForRecipe(projectId: String, repoName: String, request: IndexRefreshRequest) {
        with(request) {
            val artifactInfo = buildConanArtifactInfo(projectId, repoName, request)
            if (revision.isNullOrEmpty()) {
                refreshIndexForRecipe(artifactInfo)
            } else {
                if (packageId.isNullOrEmpty()) {
                    refreshIndexForRecipeRevision(artifactInfo)
                } else {
                    refreshIndexForRecipePackage(artifactInfo)
                }
            }
        }
    }

    override fun metadataRefresh(projectId: String, repoName: String) {
        packageSearch(projectId, repoName, false)
    }

    override fun packageMetadataRefresh(projectId: String, repoName: String, packageKey: String) {
        versionSearch(projectId, repoName, packageKey, false)
    }

    override fun versionMetadataRefresh(projectId: String, repoName: String, packageKey: String, version: String) {
        val versionInfo = packageService.findVersionByName(projectId, repoName, packageKey, version) ?: return
        refreshMetadataForVersion(projectId, repoName, versionInfo)
    }

    private fun packageSearch(projectId: String, repoName: String, indexRefresh: Boolean) {
        // 查询包
        var pageNumber = 1
        var packageOption = PackageListOption(pageNumber = pageNumber, pageSize = properties.pageSize)
        var packagePage = packageService.listPackagePage(projectId, repoName, option = packageOption)
        while (packagePage.records.isNotEmpty()) {
            packagePage.records.forEach { pkg -> versionSearch(projectId, repoName, pkg.key, indexRefresh) }
            pageNumber += 1
            packageOption = PackageListOption(pageNumber = pageNumber, pageSize = properties.pageSize)
            packagePage = packageService.listPackagePage(projectId, repoName, option = packageOption)
        }
    }

    private fun versionSearch(projectId: String, repoName: String, key: String, indexRefresh: Boolean) {
        // 查询包
        var pageNumber = 1
        var versionOption = VersionListOption(
            pageNumber = pageNumber, pageSize = properties.pageSize, sortProperty = "createdDate"
        )
        var versionPage = packageService.listVersionPage(projectId, repoName, key, option = versionOption)
        while (versionPage.records.isNotEmpty()) {
            versionPage.records.forEach {
                if (indexRefresh) {
                    indexRefreshByVersion(projectId, repoName, it)
                } else {
                    refreshMetadataForVersion(projectId, repoName, it)
                }
            }
            pageNumber += 1
            versionOption = VersionListOption(
                pageNumber = pageNumber, pageSize = properties.pageSize, sortProperty = "createdDate"
            )
            versionPage = packageService.listVersionPage(projectId, repoName, key, option = versionOption)
        }
    }

    private fun refreshMetadataForVersion(projectId: String, repoName: String, version: PackageVersion) {
        val conanFileReference = version.packageMetadata.toConanFileReference() ?: return
        val request = ConanMetadataRequest(
            projectId = projectId,
            repoName = repoName,
            name = conanFileReference.name,
            version = conanFileReference.version,
            user = conanFileReference.userName,
            channel = conanFileReference.channel,
            recipe = buildConanFileName(conanFileReference)
        )
        try {
            conanMetadataService.storeMetadata(request)
        } catch (e: Exception) {
            logger.warn("metadata refresh for $conanFileReference in $projectId|$repoName error: ${e.message}")
        }
    }

    private fun indexRefreshByVersion(projectId: String, repoName: String, version: PackageVersion) {
        val conanFileReference = version.packageMetadata.toConanFileReference() ?: return
        val artifactInfo = ConanArtifactInfo(
            projectId = projectId,
            repoName = repoName,
            artifactUri = StringPool.SLASH,
            name = conanFileReference.name,
            userName = conanFileReference.userName,
            version = conanFileReference.version,
            channel = conanFileReference.channel,
            revision = conanFileReference.revision,
            packageId = null
        )
        refreshIndexForRecipe(artifactInfo)
    }

    /**
     * 更新recipe下所有index文件
     */
    private fun refreshIndexForRecipe(artifactInfo: ConanArtifactInfo) {
        val conanFileReference = convertToConanFileReference(artifactInfo)
        val revPath = PathUtils.normalizeFullPath(getRecipeRevisionsFile(conanFileReference))
        val revPathPrefix = revPath.removeSuffix(INDEX_JSON)
        val revisionsList = mutableListOf<Pair<String, String>>()
        revisionsList.addAll(listSubFolder(artifactInfo.projectId, artifactInfo.repoName, revPathPrefix))
        revisionsList.forEach {
            artifactInfo.revision = it.first
            refreshIndexForRecipeRevision(artifactInfo)
        }
        val reference = buildConanFileName(conanFileReference)
        storeIndex(reference, artifactInfo.projectId, artifactInfo.repoName, revPath, revisionsList)
    }

    private fun storeIndex(
        reference: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        revisionsList: List<Pair<String, String>>
    ) {
        try {
            val indexInfo = IndexInfo(reference = reference)
            revisionsList.forEach {
                val date = LocalDateTime.parse(it.second, DateTimeFormatter.ISO_DATE_TIME)
                indexInfo.addRevision(RevisionInfo(it.first, TimeFormatUtil.convertToUtcTime(date)))
            }
            commonService.uploadIndexJson(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                indexInfo = indexInfo
            )
        } catch (e: Exception) {
            logger.warn("store index $fullPath in $projectId|$repoName error: ${e.message}")
        }

    }


    /**
     * 更新recipe下指定revision下的所有index文件
     */
    private fun refreshIndexForRecipeRevision(artifactInfo: ConanArtifactInfo) {
        val conanFileReference = convertToConanFileReference(artifactInfo, artifactInfo.revision)
        val revPath = PathUtils.normalizeFullPath(getPackageRevisionsFile(conanFileReference))
        val revisionsList = mutableListOf<Pair<String, String>>()
        revisionsList.addAll(listSubFolder(artifactInfo.projectId, artifactInfo.repoName, revPath))
        revisionsList.forEach {
            artifactInfo.packageId = it.first
            refreshIndexForRecipePackage(artifactInfo)
        }
    }


    /**
     * 更新recipe下指定revision下的指定packageId下所有index文件
     */
    private fun refreshIndexForRecipePackage(artifactInfo: ConanArtifactInfo) {
        try {
            val packageReference = convertToPackageReference(artifactInfo)
            val pRevPath = PathUtils.normalizeFullPath(getPackageRevisionsFile(packageReference))
            val pRevPathPrefix = pRevPath.removeSuffix(INDEX_JSON)
            val revisionsList = mutableListOf<Pair<String, String>>()
            revisionsList.addAll(listSubFolder(artifactInfo.projectId, artifactInfo.repoName, pRevPathPrefix))
            val reference = ConanPathUtils.buildPackageReference(packageReference)
            storeIndex(reference, artifactInfo.projectId, artifactInfo.repoName, pRevPath, revisionsList)
        } catch (e: Exception) {
            logger.warn("refresh index for $artifactInfo error: ${e.message}")
        }

    }

    private fun listSubFolder(projectId: String, repoName: String, path: String): List<Pair<String, String>> {
        var pageNumber = 1
        val result = mutableListOf<Pair<String, String>>()
        do {
            val option = NodeListOption(
                pageNumber = pageNumber,
                pageSize = properties.pageSize,
                includeFolder = true,
                includeMetadata = false,
                deep = false
            )
            val records =
                nodeService.listNodePage(
                    ArtifactInfo(projectId, repoName, path), option
                ).records
            if (records.isEmpty()) {
                break
            }
            result.addAll(records.filter { it.folder }.map { Pair(it.name, it.lastModifiedDate) })
            pageNumber++
        } while (records.size == properties.pageSize)
        return result
    }

    private fun buildConanArtifactInfo(
        projectId: String,
        repoName: String,
        request: IndexRefreshRequest
    ): ConanArtifactInfo {
        with(request) {
            return ConanArtifactInfo(
                projectId = projectId,
                repoName = repoName,
                artifactUri = StringPool.SLASH,
                name = name,
                userName = userName,
                version = version,
                channel = channel,
                revision = revision,
                packageId = packageId
            )
        }
    }


    companion object {
        val logger: Logger = LoggerFactory.getLogger(ConanExtServiceImpl::class.java)
    }
}
