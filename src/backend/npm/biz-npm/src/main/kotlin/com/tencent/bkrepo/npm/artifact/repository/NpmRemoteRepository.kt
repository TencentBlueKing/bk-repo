package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.DISTTAGS
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.LATEST
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_FULL_PATH
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.Request
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects

@Component
class NpmRemoteRepository : RemoteRepository() {

    var isWithFileVersion: Boolean = false

    var isSpecifiedVersion: Boolean = false

    override fun onDownload(context: ArtifactDownloadContext): File? {
        getCacheArtifact(context)?.let { return it }
        return super.onDownload(context)
    }

    override fun generateRemoteDownloadUrl(context: ArtifactTransferContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        return remoteConfiguration.url.trimEnd('/') + artifactUri
    }

    override fun getCacheNodeCreateRequest(context: ArtifactDownloadContext, file: File): NodeCreateRequest {
        val repositoryInfo = context.repositoryInfo
        val sha256 = FileDigestUtils.fileSha256(listOf(file.inputStream()))
        val md5 = FileDigestUtils.fileMd5(listOf(file.inputStream()))
        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String,
            size = file.length(),
            sha256 = sha256,
            md5 = md5,
            overwrite = true,
            operator = context.userId
        )
    }

    private fun getCacheArtifact(context: ArtifactTransferContext): File? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        if (!cacheConfiguration.cacheEnabled) return null

        val repositoryInfo = context.repositoryInfo
        val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        val node = nodeResource.detail(repositoryInfo.projectId, repositoryInfo.name, fullPath).data ?: return null
        if (node.nodeInfo.folder) return null
        val createdDate = LocalDateTime.parse(node.nodeInfo.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val age = Duration.between(createdDate, LocalDateTime.now()).toMinutes()
        return if (age <= cacheConfiguration.cachePeriod) {
            val file = storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
            file?.let { logger.debug("Cached remote artifact[${context.artifactInfo.getFullUri()}] is hit") }
            file
        } else null
    }

    override fun search(context: ArtifactSearchContext): JsonObject? {
        getCacheArtifact(context)?.let {
            // 下载指定版本号的安装包时需要将对应版本的json文件缓存
            putPkgVersionCache(context, it)
            return transFileToJson(context, it)
        }
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val httpClient = createHttpClient(remoteConfiguration)
        val searchUri = generateRemoteSearchUrl(context)
        val request = Request.Builder().url(searchUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            val file = createTempFile(response.body()!!)
            putArtifactCache(context, file)
            transFileToJson(context, file)
        } else null
    }

    private fun transFileToJson(context: ArtifactSearchContext, file: File): JsonObject {
        val pkgJson = GsonUtils.transferFileToJson(file)
        val name = pkgJson.get(NAME).asString
        val id = pkgJson[ID].asString
        val tarball = getTarball(context)
        if (id.contains('@')) {
            val version = pkgJson[VERSION]
            val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version)
            pkgJson.getAsJsonObject(DIST).addProperty(TARBALL, tarball + tgzFullPath)
        } else {
            val versions = pkgJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val versionObject = versions.getAsJsonObject(it)
                val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, it)
                // 替换tarball
                versionObject.getAsJsonObject(DIST).addProperty(TARBALL, tarball + tgzFullPath)
            }
        }
        return pkgJson
    }

    private fun getTarball(context: ArtifactSearchContext): String {
        val request = HttpContextHolder.getRequest()
        val requestURL = request.requestURL.toString()
        val requestURI = request.requestURI
        val projectId = context.artifactInfo.projectId
        val repoName = context.artifactInfo.repoName
        val replace = requestURL.replace(requestURI, "")
        return "$replace/$projectId/$repoName"
    }

    private fun putArtifactCache(context: ArtifactSearchContext, file: File) {
        val jsonObj = GsonUtils.transferFileToJson(file)
        val version = getNpmVersion(jsonObj)
        val name = jsonObj.get(NAME).asString
        putNpmPkgArtifactCache(context, jsonObj)

        if (!isWithFileVersion) {
            context.contextAttributes[NPM_FILE_FULL_PATH] =
                String.format(NPM_PKG_VERSION_FULL_PATH, name, name, version)
            val pkgVersionJson = jsonObj.getAsJsonObject(VERSIONS).getAsJsonObject(version)
            putNpmPkgArtifactCache(context, pkgVersionJson)
        }
    }

    private fun getNpmVersion(jsonObj: JsonObject): String {
        val id = jsonObj[ID].asString
        val latestVersion = if (!id.contains('@')) {
            jsonObj.getAsJsonObject(DISTTAGS).get(LATEST).asString
        } else {
            isWithFileVersion = true
            id.split('@')[1]
        }
        val header = HttpContextHolder.getRequest().getHeader("referer")
        return if (StringUtils.isBlank(header) || header.split('@').size < 2) {
            latestVersion
        } else {
            isSpecifiedVersion = true
            header.split('@')[1]
        }
    }

    private fun putPkgVersionCache(context: ArtifactSearchContext, file: File) {
        val jsonObj = GsonUtils.transferFileToJson(file)
        val version = getNpmVersion(jsonObj)
        if (isSpecifiedVersion) {
            val name = jsonObj.get(NAME).asString
            context.contextAttributes[NPM_FILE_FULL_PATH] =
                String.format(NPM_PKG_VERSION_FULL_PATH, name, name, version)

            val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
            val cacheConfiguration = remoteConfiguration.cacheConfiguration
            // if (!cacheConfiguration.cacheEnabled) return

            val repositoryInfo = context.repositoryInfo
            val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
            val node = nodeResource.detail(repositoryInfo.projectId, repositoryInfo.name, fullPath).data
            if (Objects.isNull(node)) {
                val pkgVersionJson = jsonObj.getAsJsonObject(VERSIONS).getAsJsonObject(version)
                putNpmPkgArtifactCache(context, pkgVersionJson)
                return
            }
            if (node!!.nodeInfo.folder) return
            val createdDate = LocalDateTime.parse(node.nodeInfo.createdDate, DateTimeFormatter.ISO_DATE_TIME)
            val age = Duration.between(createdDate, LocalDateTime.now()).toMinutes()
            if (age >= cacheConfiguration.cachePeriod) {
                val pkgVersionJson = jsonObj.getAsJsonObject(VERSIONS).getAsJsonObject(version)
                putNpmPkgArtifactCache(context, pkgVersionJson)
            }
        }
    }

    private fun putNpmPkgArtifactCache(context: ArtifactSearchContext, jsonObj: JsonObject?) {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        val pkgFile = ArtifactFileFactory.build()
        Streams.copy(
            GsonUtils.gson.toJson(jsonObj).byteInputStream(),
            pkgFile.getOutputStream(),
            true
        )
        if (cacheConfiguration.cacheEnabled) {
            val nodeCreateRequest = getNodeCreateRequest(context, pkgFile)
            nodeResource.create(nodeCreateRequest)
            storageService.store(nodeCreateRequest.sha256!!, pkgFile, context.storageCredentials)
        }
    }

    private fun generateRemoteSearchUrl(context: ArtifactSearchContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + artifactUri
    }

    private fun getNodeCreateRequest(context: ArtifactSearchContext, file: ArtifactFile): NodeCreateRequest {
        val repositoryInfo = context.repositoryInfo
        val sha256 = FileDigestUtils.fileSha256(listOf(file.getInputStream()))
        val md5 = FileDigestUtils.fileMd5(listOf(file.getInputStream()))
        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String,
            size = file.getSize(),
            sha256 = sha256,
            md5 = md5,
            overwrite = true,
            operator = context.userId
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmLocalRepository::class.java)
    }
}
