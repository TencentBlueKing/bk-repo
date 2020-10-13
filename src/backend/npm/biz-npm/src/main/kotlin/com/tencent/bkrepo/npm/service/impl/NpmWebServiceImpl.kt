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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.npm.service.impl

import com.google.gson.JsonObject
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DEV_DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_JSON_FILE
import com.tencent.bkrepo.npm.constants.NPM_PKG_JSON_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.REPO_TYPE
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.exception.NpmArgumentNotFoundException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.user.BasicInfo
import com.tencent.bkrepo.npm.pojo.user.DependenciesInfo
import com.tencent.bkrepo.npm.pojo.user.DownloadCount
import com.tencent.bkrepo.npm.pojo.user.NpmPackageLatestVersionInfo
import com.tencent.bkrepo.npm.pojo.user.request.PackageDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.npm.pojo.user.VersionDependenciesInfo
import com.tencent.bkrepo.npm.service.AbstractNpmService
import com.tencent.bkrepo.npm.service.ModuleDepsService
import com.tencent.bkrepo.npm.service.NpmService
import com.tencent.bkrepo.npm.service.NpmWebService
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetric
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NpmWebServiceImpl : NpmWebService, AbstractNpmService() {

    @Autowired
    private lateinit var moduleDepsService: ModuleDepsService

    @Autowired
    private lateinit var npmService: NpmService

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun detailVersion(artifactInfo: NpmArtifactInfo, packageKey: String, version: String): PackageVersionInfo {
        val name = PackageKeys.resolveNpm(packageKey)
        val versionsInfo = searchPkgInfo(name).getAsJsonObject(VERSIONS)
        if (!versionsInfo.keySet()
                .contains(version)
        ) throw NpmArtifactNotFoundException("version [$version] don't found in package [$name].")
        val tarball = versionsInfo.getAsJsonObject(version).getAsJsonObject(DIST)[TARBALL].asString
        val nodeFullPath = tarball.substring(tarball.indexOf(name) - 1, tarball.length)
        with(artifactInfo) {
            checkRepositoryExist(projectId, repoName)
            val nodeDetail = nodeClient.detail(projectId, repoName, REPO_TYPE, nodeFullPath).data ?: run {
                logger.warn("node [$nodeFullPath] don't found.")
                throw NpmArtifactNotFoundException("node [$nodeFullPath] don't found.")
            }
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data ?: run {
                logger.warn("packageKey [$packageKey] don't found.")
                throw NpmArtifactNotFoundException("packageKey [$packageKey] don't found.")
            }
            val basicInfo = buildBasicInfo(nodeDetail, packageVersion)
            val versionDependenciesInfo =
                queryVersionDependenciesInfo(artifactInfo, versionsInfo.getAsJsonObject(version), name)
            return PackageVersionInfo(basicInfo, emptyMap(), versionDependenciesInfo)
        }
    }

    private fun queryVersionDependenciesInfo(
        artifactInfo: NpmArtifactInfo,
        versionInfo: JsonObject,
        name: String
    ): VersionDependenciesInfo {
        val moduleDepsPage = moduleDepsService.page(
            artifactInfo.projectId,
            artifactInfo.repoName,
            DEFAULT_PAGE_NUMBER,
            DEFAULT_PAGE_SIZE,
            name
        )
        val dependenciesList = parseDependencies(versionInfo)
        val devDependenciesList = parseDevDependencies(versionInfo)
        return VersionDependenciesInfo(dependenciesList, devDependenciesList, moduleDepsPage)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deletePackage(deleteRequest: PackageDeleteRequest) {
        logger.info("npm delete package request: [$deleteRequest]")
        with(deleteRequest) {
            checkRepositoryExist(projectId, repoName)
            val scope = if (name.contains('/')) name.substringBefore('/') else ""
            val pkgName = name.substringAfter('/')
            val artifactInfo = NpmArtifactInfo(projectId, repoName, "", scope, pkgName, "")
            npmService.unpublish(operator, artifactInfo)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteVersion(deleteRequest: PackageVersionDeleteRequest) {
        logger.info("npm delete package version request: [$deleteRequest]")
        with(deleteRequest) {
            checkRepositoryExist(projectId, repoName)
            val scope = if (name.contains('/')) name.substringBefore('/') else ""
            val pkgName = name.substringAfter('/')
            val artifactInfo = NpmArtifactInfo(projectId, repoName, "", scope, pkgName, "")
            val packageInfo = searchPkgInfo(name)
            val versionEntries = packageInfo.getAsJsonObject(VERSIONS).entrySet()
            val iterator = versionEntries.iterator()
            // 如果删除最后一个版本直接删除整个包
            if (versionEntries.size == 1 && iterator.hasNext() && iterator.next().key == version){
                val deletePackageRequest = PackageDeleteRequest(projectId, repoName, name, operator)
                deletePackage(deletePackageRequest)
                return
            }
            npmService.unPublishPkgWithVersion(operator, artifactInfo, name, version)
            // 修改package.json文件的内容
            updatePackageWithDeleteVersion(this, packageInfo)
        }
    }

    fun updatePackageWithDeleteVersion(deleteRequest: PackageVersionDeleteRequest, packageInfo: JsonObject) {
        with(deleteRequest) {
            val latest = packageInfo.getAsJsonObject(DISTTAGS)[LATEST].asString
            if (version != latest) {
                // 删除versions里面对应的版本
                packageInfo.getAsJsonObject(VERSIONS).remove(version)
                packageInfo.getAsJsonObject(TIME).remove(version)
                val iterator = packageInfo.getAsJsonObject(DISTTAGS).entrySet().iterator()
                while (iterator.hasNext()) {
                    if (version == iterator.next().value.asString) {
                        iterator.remove()
                    }
                }
            } else {
                val newLatest = packageClient.findPackageByKey(projectId, repoName, PackageKeys.ofNpm(name)).data?.latest
                    ?: run {
                        logger.error("delete version by web operator to find new latest version failed with package [$name]")
                        throw NpmArtifactNotFoundException("delete version by web operator to find new latest version failed with package [$name]")
                }
                packageInfo.getAsJsonObject(VERSIONS).remove(version)
                packageInfo.getAsJsonObject(TIME).remove(version)
                packageInfo.getAsJsonObject(DISTTAGS).addProperty(LATEST, newLatest)
            }
            reUploadPackageJsonFile(packageInfo)

        }
    }

    fun reUploadPackageJsonFile(packageInfo: JsonObject) {
        val name = packageInfo[NAME].asString
        val artifactFileMap = ArtifactFileMap()
        val artifactFile = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(packageInfo))
        artifactFileMap[NPM_PACKAGE_JSON_FILE] = artifactFile
        val context = ArtifactUploadContext(artifactFileMap)
        context.putAttribute(NPM_PKG_JSON_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, name))
        ArtifactContextHolder.getRepository().upload(context)
        artifactFile.delete()
    }

    private fun parseDependencies(versionJsonObject: JsonObject): MutableList<DependenciesInfo> {
        val dependenciesList: MutableList<DependenciesInfo> = mutableListOf()
        if (versionJsonObject.has(DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEPENDENCIES).entrySet().forEach { (key, value) ->
                dependenciesList.add(
                    DependenciesInfo(
                        key,
                        value.asString
                    )
                )
            }
        }
        return dependenciesList
    }

    private fun parseDevDependencies(versionJsonObject: JsonObject): MutableList<DependenciesInfo> {
        val devDependenciesList: MutableList<DependenciesInfo> = mutableListOf()
        if (versionJsonObject.has(DEV_DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).entrySet().forEach { (key, value) ->
                devDependenciesList.add(
                    DependenciesInfo(
                        key,
                        value.asString
                    )
                )
            }
        }
        return devDependenciesList
    }

    private fun searchPkgInfo(pkgName: String): JsonObject {
        pkgName.takeIf { !pkgName.isBlank() } ?: throw NpmArgumentNotFoundException("argument [$pkgName] not found.")
        val context = ArtifactQueryContext()
        context.putAttribute(NPM_FILE_FULL_PATH, String.format(NPM_PKG_METADATA_FULL_PATH, pkgName))
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        return repository.query(context)?.let { it as JsonObject }
            ?: throw NpmArtifactNotFoundException("package [$pkgName] not found.")
    }

    companion object {

        val logger: Logger = LoggerFactory.getLogger(NpmWebServiceImpl::class.java)

        fun buildBasicInfo(nodeDetail: NodeDetail, packageVersion: PackageVersion): BasicInfo {
            with(nodeDetail) {
                return BasicInfo(
                    packageVersion.name,
                    fullPath,
                    size,
                    sha256!!,
                    md5!!,
                    packageVersion.stageTag,
                    projectId,
                    repoName,
                    packageVersion.downloads,
                    createdBy,
                    createdDate,
                    lastModifiedBy,
                    lastModifiedDate
                )
            }
        }

        fun convert(downloadStatisticsMetric: DownloadStatisticsMetric): DownloadCount {
            with(downloadStatisticsMetric) {
                return DownloadCount(description, count)
            }
        }

        fun convert(nodeDetail: NodeDetail): NpmPackageLatestVersionInfo {
            with(nodeDetail) {
                return NpmPackageLatestVersionInfo(
                    createdBy,
                    createdDate,
                    lastModifiedBy,
                    lastModifiedDate,
                    name,
                    size,
                    null,
                    stageTag,
                    projectId,
                    repoName
                )
            }
        }
    }
}