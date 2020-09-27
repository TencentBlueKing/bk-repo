package com.tencent.bkrepo.npm.service.impl

import com.google.gson.JsonObject
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DEV_DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.REPO_TYPE
import com.tencent.bkrepo.npm.constants.TARBALL
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
import com.tencent.bkrepo.repository.api.DownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetric
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NpmWebServiceImpl : NpmWebService, AbstractNpmService() {

    @Autowired
    private lateinit var moduleDepsService: ModuleDepsService

    @Autowired
    private lateinit var downloadStatisticsClient: DownloadStatisticsClient

    @Autowired
    private lateinit var npmService: NpmService

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun detailVersion(artifactInfo: NpmArtifactInfo, name: String, version: String): PackageVersionInfo {
        val versionsInfo = searchPkgInfo(name).getAsJsonObject(VERSIONS)
        if (!versionsInfo.keySet().contains(version)) throw NpmArtifactNotFoundException("version [$version] don't found in package [$name].")
        val tarball = versionsInfo.getAsJsonObject(version).getAsJsonObject(DIST)[TARBALL].asString
        val nodeFullPath = tarball.substring(tarball.indexOf(name) - 1, tarball.length)
        with(artifactInfo) {
            val nodeDetail = nodeClient.detail(projectId, repoName, REPO_TYPE, nodeFullPath).data ?: run {
                logger.warn("node [$nodeFullPath] don't found.")
                throw NpmArtifactNotFoundException("node [$nodeFullPath] don't found.")
            }
            with(nodeDetail) {
                val downloadCount = downloadStatisticsClient.query(projectId, repoName, name, version).data!!.count
                val basicInfo = BasicInfo(
                    version,
                    fullPath,
                    size,
                    sha256!!,
                    md5!!,
                    stageTag,
                    projectId,
                    repoName,
                    downloadCount,
                    createdBy,
                    createdDate,
                    lastModifiedBy,
                    lastModifiedDate
                )
                val versionDependenciesInfo = queryVersionDependenciesInfo(artifactInfo, versionsInfo.getAsJsonObject(version), name)
                return PackageVersionInfo(basicInfo, emptyMap(), versionDependenciesInfo)
            }
        }
    }

    private fun queryVersionDependenciesInfo(
        artifactInfo: NpmArtifactInfo,
        versionInfo: JsonObject,
        name: String
    ): VersionDependenciesInfo {
        val moduleDepsPage = moduleDepsService.page(artifactInfo.projectId, artifactInfo.repoName, DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE, name)
        val dependenciesList = parseDependencies(versionInfo)
        val devDependenciesList = parseDevDependencies(versionInfo)
        return VersionDependenciesInfo(dependenciesList, devDependenciesList, moduleDepsPage)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deletePackage(deleteRequest: PackageDeleteRequest) {
        logger.info("npm delete package request: [$deleteRequest]")
        with(deleteRequest) {
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
            val scope = if (name.contains('/')) name.substringBefore('/') else ""
            val pkgName = name.substringAfter('/')
            val artifactInfo = NpmArtifactInfo(projectId, repoName, "", scope, pkgName, "")
            npmService.unPublishPkgWithVersion(operator, artifactInfo, name, version)
        }
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