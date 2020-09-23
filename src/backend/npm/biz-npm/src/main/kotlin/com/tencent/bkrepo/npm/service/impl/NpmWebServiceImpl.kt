package com.tencent.bkrepo.npm.service.impl

import com.google.gson.JsonObject
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DEV_DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.MAINTAINERS
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.README
import com.tencent.bkrepo.npm.constants.REPO_TYPE
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.exception.NpmArgumentNotFoundException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.DependenciesInfo
import com.tencent.bkrepo.npm.pojo.DownloadCount
import com.tencent.bkrepo.npm.pojo.MaintainerInfo
import com.tencent.bkrepo.npm.pojo.PackageInfoResponse
import com.tencent.bkrepo.npm.pojo.TagsInfo
import com.tencent.bkrepo.npm.pojo.user.NpmPackageInfo
import com.tencent.bkrepo.npm.pojo.user.NpmPackageLatestVersionInfo
import com.tencent.bkrepo.npm.pojo.user.NpmPackageVersionInfo
import com.tencent.bkrepo.npm.pojo.user.PackageDeleteRequest
import com.tencent.bkrepo.npm.service.AbstractNpmService
import com.tencent.bkrepo.npm.service.ModuleDepsService
import com.tencent.bkrepo.npm.service.NpmService
import com.tencent.bkrepo.npm.service.NpmWebService
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.repository.api.DownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetric
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeQueryBuilder
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
    override fun queryPackageInfo(artifactInfo: NpmArtifactInfo): PackageInfoResponse {
        val pkgName = artifactInfo.artifactUri.trimStart('/')
        val packageJson = searchPkgInfo(pkgName)
        val page = moduleDepsService.page(artifactInfo.projectId, artifactInfo.repoName, PAGE, SIZE, pkgName)
        val query = downloadStatisticsClient.queryForSpecial(
            artifactInfo.projectId, artifactInfo.repoName, artifactInfo.artifactUri
        )
        val latestVersion = packageJson.getAsJsonObject(DISTTAGS).get(LATEST).asString
        val versionJsonObject = packageJson.getAsJsonObject(VERSIONS).getAsJsonObject(latestVersion)
        val timeJsonObject = packageJson.getAsJsonObject(TIME)

        val currentTags = parseDistTags(packageJson, timeJsonObject)
        val versionsList = parseVersions(timeJsonObject)
        val maintainersList = parseMaintainers(packageJson)
        val dependenciesList = parseDependencies(versionJsonObject)
        val devDependenciesList = parseDevDependencies(versionJsonObject)
        return PackageInfoResponse(
            packageJson[NAME].asString,
            packageJson[DESCRIPTION].asString,
            packageJson[README].asString,
            currentTags,
            versionsList,
            maintainersList,
            query.data!!.statisticsMetrics.map { convert(it) },
            dependenciesList,
            devDependenciesList,
            page
        )
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun queryPkgList(
        userId: String?,
        artifactInfo: NpmArtifactInfo,
        pageNumber: Int,
        pageSize: Int,
        name: String?,
        stageTag: String?
    ): Page<NpmPackageInfo> {
        with(artifactInfo) {
            checkRepositoryExist(projectId, repoName)
            // 查询所有的包 根据package.json来查询
            val queryModelBuilder = NodeQueryBuilder().select(
                "path"
            ).sortByDesc("lastModifyDate").page(pageNumber, pageSize).projectId(projectId).repoName(repoName)
                .and().name("package.json")
            name?.let { queryModelBuilder.path("$it/", OperationType.SUFFIX) }
            val result = nodeClient.query(queryModelBuilder.build()).data
            val packageInfoList = result?.records?.map {
                val fullPath = it["path"] as String
                val packageName = fullPath.substringAfter("/.npm/").substringBeforeLast('/')
                val scope = if (packageName.contains('@')) packageName.substringBefore('/') else ""
                val pkgName = if (packageName.contains('@')) packageName.substringAfter('/') else packageName
                val npmArtifactInfo = NpmArtifactInfo(projectId, repoName, artifactUri, scope, pkgName, LATEST)
                val npmPackageMetadata = npmService.searchPackageInfo(npmArtifactInfo)!!
                val latestVersion = npmPackageMetadata.get(VERSION).asString
                val tgzFullPath = NpmUtils.getTgzPath(packageName, latestVersion)
                val detail = nodeClient.detail(projectId, repoName, REPO_TYPE, tgzFullPath).data!!
                NpmPackageInfo(packageName, convert(detail).copy(name = packageName, version = latestVersion))
            } ?: emptyList()
            with(result!!) {
                return Page(pageNumber, pageSize, totalRecords, totalPages, packageInfoList)
            }
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun queryPkgVersionList(
        userId: String?,
        artifactInfo: NpmArtifactInfo,
        pageNumber: Int,
        pageSize: Int,
        name: String
    ): Page<NpmPackageVersionInfo> {
        with(artifactInfo) {
            checkRepositoryExist(projectId, repoName)
            // 查询所有的tgz包
            val queryModelBuilder = NodeQueryBuilder().select(
                "name", "metadata", "size", "lastModifiedDate", "lastModifiedBy"
            ).sortByDesc("lastModifiedDate").page(pageNumber, pageSize).projectId(projectId).repoName(repoName)
                .and().path("/$name", OperationType.PREFIX).excludeFolder()
            val result = nodeClient.query(queryModelBuilder.build()).data
            val versionInfoList = result?.records?.map {
                val metadata = it["metadata"] as Map<*, *>
                var version = metadata["version"] as? String
                version = if (version == null) {
                    val pkgName = it["name"] as String
                    val finalVersion = NpmUtils.analyseVersionFromPackageName(pkgName)
                    finalVersion
                } else version
                val size = it["size"] as Int
                val stageTag = it["stageTag"] as? String
                val lastModifiedBy = it["lastModifiedBy"] as String
                val lastModifiedDate = it["lastModifiedDate"] as String
                NpmPackageVersionInfo(
                    name,
                    size.toLong(),
                    version,
                    stageTag,
                    lastModifiedBy,
                    lastModifiedDate,
                    projectId,
                    repoName
                )
            } ?: emptyList()
            with(result!!) {
                return Page(pageNumber, pageSize, totalRecords, totalPages, versionInfoList)
            }
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    override fun deletePackage(deleteRequest: PackageDeleteRequest) {

    }

    private fun parseDistTags(packageJson: JsonObject, timeJsonObject: JsonObject): MutableList<TagsInfo> {
        val currentTags: MutableList<TagsInfo> = mutableListOf()
        packageJson.getAsJsonObject(DISTTAGS).entrySet().forEach { (key, value) ->
            val time = timeJsonObject[value.asString].asString
            currentTags.add(TagsInfo(tags = key, version = value.asString, time = time))
        }
        return currentTags
    }

    private fun parseVersions(timeJsonObject: JsonObject): MutableList<TagsInfo> {
        val versionsList: MutableList<TagsInfo> = mutableListOf()
        timeJsonObject.entrySet().forEach { (key, value) ->
            if (!(key == "created" || key == "modified")) {
                versionsList.add(TagsInfo(version = key, time = value.asString))
            }
        }
        return versionsList
    }

    private fun parseMaintainers(packageJson: JsonObject): MutableList<MaintainerInfo> {
        val maintainersList: MutableList<MaintainerInfo> = mutableListOf()
        packageJson.getAsJsonArray(MAINTAINERS)?.forEach {
            it.asJsonObject.entrySet().forEach { (key, value) ->
                maintainersList.add(MaintainerInfo(key, value.asString))
            }
        }
        return maintainersList
    }

    private fun parseDependencies(versionJsonObject: JsonObject): MutableList<DependenciesInfo> {
        val dependenciesList: MutableList<DependenciesInfo> = mutableListOf()
        if (versionJsonObject.has(DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEPENDENCIES).entrySet().forEach { (key, value) ->
                dependenciesList.add(DependenciesInfo(key, value.asString))
            }
        }
        return dependenciesList
    }

    private fun parseDevDependencies(versionJsonObject: JsonObject): MutableList<DependenciesInfo> {
        val devDependenciesList: MutableList<DependenciesInfo> = mutableListOf()
        if (versionJsonObject.has(DEV_DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).entrySet().forEach { (key, value) ->
                devDependenciesList.add(DependenciesInfo(key, value.asString))
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
        const val PAGE = 0
        const val SIZE = 20

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