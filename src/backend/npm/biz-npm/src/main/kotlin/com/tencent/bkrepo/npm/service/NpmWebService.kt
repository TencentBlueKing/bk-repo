package com.tencent.bkrepo.npm.service

import com.google.gson.JsonObject
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.constants.DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DEV_DEPENDENCIES
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.MAINTAINERS
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_FULL_PATH
import com.tencent.bkrepo.npm.constants.README
import com.tencent.bkrepo.npm.constants.TIME
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.exception.NpmArgumentNotFoundException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.DependenciesInfo
import com.tencent.bkrepo.npm.pojo.DownloadCount
import com.tencent.bkrepo.npm.pojo.MaintainerInfo
import com.tencent.bkrepo.npm.pojo.PackageInfoResponse
import com.tencent.bkrepo.npm.pojo.TagsInfo
import com.tencent.bkrepo.repository.api.ArtifactDownloadCountResource
import com.tencent.bkrepo.repository.pojo.download.count.SpecialDayCount
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NpmWebService {

    @Autowired
    private lateinit var moduleDepsService: ModuleDepsService

    @Autowired
    private lateinit var downloadCountResource: ArtifactDownloadCountResource

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun getPackageInfo(artifactInfo: NpmArtifactInfo): PackageInfoResponse {
        val pkgName = artifactInfo.artifactUri.trimStart('/')
        val packageJson = searchPkgInfo(pkgName)
        val page = moduleDepsService.page(artifactInfo.projectId, artifactInfo.repoName, 0, 20, pkgName)
        val query = downloadCountResource.query(artifactInfo.projectId, artifactInfo.repoName, artifactInfo.artifactUri)

        val latestVersion = packageJson.getAsJsonObject(DISTTAGS).get(LATEST).asString
        val currentTags: MutableList<TagsInfo> = mutableListOf()
        val versionsList: MutableList<TagsInfo> = mutableListOf()
        val maintainersList: MutableList<MaintainerInfo> = mutableListOf()
        val dependenciesList: MutableList<DependenciesInfo> = mutableListOf()
        val devDependenciesList: MutableList<DependenciesInfo> = mutableListOf()

        val timeJsonObject = packageJson.getAsJsonObject(TIME)
        packageJson.getAsJsonObject(DISTTAGS).entrySet().forEach { (key, value) ->
            val time = timeJsonObject[value.asString].asString
            currentTags.add(TagsInfo(tags = key, version = value.asString, time = time))
        }
        timeJsonObject.entrySet().forEach { (key, value) ->
            if (!(key == "created" || key == "modified")) {
                versionsList.add(TagsInfo(version = key, time = value.asString))
            }
        }
        packageJson.getAsJsonArray(MAINTAINERS)?.forEach {
            it.asJsonObject.entrySet().forEach { (key, value) ->
                maintainersList.add(MaintainerInfo(key, value.asString))
            }
        }
        val versionJsonObject = packageJson.getAsJsonObject(VERSIONS).getAsJsonObject(latestVersion)
        if (versionJsonObject.has(DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEPENDENCIES).entrySet().forEach { (key, value) ->
                dependenciesList.add(DependenciesInfo(key, value.asString))
            }
        }
        if (versionJsonObject.has(DEV_DEPENDENCIES) && !versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).isJsonNull) {
            versionJsonObject.getAsJsonObject(DEV_DEPENDENCIES).entrySet().forEach { (key, value) ->
                devDependenciesList.add(DependenciesInfo(key, value.asString))
            }
        }
        return PackageInfoResponse(
            packageJson[NAME].asString,
            packageJson[DESCRIPTION].asString,
            packageJson[README].asString,
            currentTags,
            versionsList,
            maintainersList,
            query.data!!.dayCount.map { convert(it) },
            dependenciesList,
            devDependenciesList,
            page
        )
    }

    private fun searchPkgInfo(pkgName: String): JsonObject {
        pkgName.takeIf { !pkgName.isBlank() } ?: throw NpmArgumentNotFoundException("argument [$pkgName] not found.")
        val context = ArtifactSearchContext()
        context.contextAttributes[NPM_FILE_FULL_PATH] = String.format(NPM_PKG_FULL_PATH, pkgName)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        return repository.search(context)?.let { it as JsonObject }
            ?: throw NpmArtifactNotFoundException("package [$pkgName] not found.")
    }

    companion object {
        fun convert(dayCount: SpecialDayCount): DownloadCount {
            with(dayCount) {
                return DownloadCount(description, count)
            }
        }
    }
}
