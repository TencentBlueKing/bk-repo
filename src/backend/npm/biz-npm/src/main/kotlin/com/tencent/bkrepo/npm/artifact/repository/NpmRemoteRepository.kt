package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_FULL_PATH
import com.tencent.bkrepo.npm.constants.OBJECTS
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.exception.NpmArgumentResolverException
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
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

@Component
class NpmRemoteRepository : RemoteRepository() {

    override fun download(context: ArtifactDownloadContext) {
        val file = this.onDownload(context) ?: throw ArtifactNotFoundException("Artifact[${context.artifactInfo.getFullUri()}] not found")
        // val name = NodeUtils.getName(context.artifactInfo.artifactUri)
        // ServletResponseUtils.response(name, file)
        super.onDownloadSuccess(context, file)
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        getCacheArtifact(context)?.let { return it }
        val tgzFile = super.onDownload(context)
        installPkgVersionFile(context)
        return tgzFile
    }

    override fun generateRemoteDownloadUrl(context: ArtifactTransferContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val tarballPrefix = getTarballPrefix(context)
        val queryString = context.request.queryString
        val requestURL =
            context.request.requestURL.toString() + if (StringUtils.isNotEmpty(queryString)) "?$queryString" else ""
        context.contextAttributes[NPM_FILE_FULL_PATH] = requestURL.removePrefix(tarballPrefix)
        return requestURL.replace(tarballPrefix, remoteConfiguration.url.trimEnd('/'))
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

    /**
     * install pkg-version json file when download tgzFile
     */
    fun installPkgVersionFile(context: ArtifactDownloadContext) {
        val tgzFullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        val pkgInfo = parseArtifactInfo(tgzFullPath)
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val httpClient = createHttpClient(remoteConfiguration)
        val searchUri = buildRemoteUri(context, pkgInfo)
        val request = Request.Builder().url(searchUri).build()
        val response = httpClient.newCall(request).execute()
        if (checkResponse(response)) {
            val file = response.body()?.let { createTempFile(it) } ?: throw NpmArtifactNotFoundException("file $tgzFullPath download failed.")
            val jsonFile = transFileToJson(context, file)
            val versionFile = jsonFile.getAsJsonObject(VERSIONS).getAsJsonObject(pkgInfo.third)
            val artifactFile = ArtifactFileFactory.build(0)
            Streams.copy(GsonUtils.gsonToInputStream(versionFile), artifactFile.getOutputStream(), true)
            val name = jsonFile[NAME].asString
            context.contextAttributes[NPM_FILE_FULL_PATH] =
                String.format(NPM_PKG_VERSION_FULL_PATH, name, name, pkgInfo.third)
            putArtifactCache(context, artifactFile.getTempFile())
        }
    }

    private fun buildRemoteUri(context: ArtifactTransferContext, pkgInfo: Triple<String, String, String>): String {
        val projectId = context.artifactInfo.projectId
        val repoName = context.artifactInfo.repoName
        val url = HttpContextHolder.getRequest().requestURL.toString().substringBeforeLast("/$projectId/")
        val remoteUrl = "$url/$projectId/$repoName/${pkgInfo.first}/${pkgInfo.second}"
        logger.info("request remote url for package info : $remoteUrl")
        return remoteUrl
    }

    private fun parseArtifactInfo(tgzFullPath: String): Triple<String, String, String> {
        val pkgVersion = tgzFullPath.substringAfterLast('/').substringBeforeLast(".tgz")
        val split = tgzFullPath.trimStart('/').split('/')
        val scope = if (split[0].startsWith('@')) split[0] else StringPool.EMPTY
        val pkgName = if (!split[0].startsWith('@')) split[0] else split[1]
        val version = pkgVersion.substring(pkgName.length + 1)
        if (split.size < 2) throw NpmArgumentResolverException("artifact $scope/$pkgName/$version resolver failed!")
        return Triple(scope, pkgName, version)
    }

    override fun search(context: ArtifactSearchContext): JsonObject? {
        getCacheArtifact(context)?.let {
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

    private fun transFileToJson(context: ArtifactTransferContext, file: File): JsonObject {
        val pkgJson = GsonUtils.transferFileToJson(file)
        val name = pkgJson.get(NAME).asString
        val id = pkgJson[ID].asString
        if (id.substring(1).contains('@')) {
            val oldTarball = pkgJson.getAsJsonObject(DIST)[TARBALL].asString
            val prefix = oldTarball.split(name)[0].trimEnd('/')
            val newTarball = oldTarball.replace(prefix, getTarballPrefix(context))
            pkgJson.getAsJsonObject(DIST).addProperty(TARBALL, newTarball)
        } else {
            val versions = pkgJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val versionObject = versions.getAsJsonObject(it)
                val oldTarball = versionObject.getAsJsonObject(DIST)[TARBALL].asString
                val prefix = oldTarball.split(name)[0].trimEnd('/')
                val newTarball = oldTarball.replace(prefix, getTarballPrefix(context))
                versionObject.getAsJsonObject(DIST).addProperty(TARBALL, newTarball)
            }
        }
        return pkgJson
    }

    private fun getTarballPrefix(context: ArtifactTransferContext): String {
        val requestURL = context.request.requestURL.toString()
        val requestURI = context.request.requestURI
        val projectId = context.artifactInfo.projectId
        val repoName = context.artifactInfo.repoName
        val replace = requestURL.replace(requestURI, "")
        return "$replace/$projectId/$repoName"
    }

    private fun putArtifactCache(context: ArtifactTransferContext, file: File) {
        val jsonObj = GsonUtils.transferFileToJson(file)
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
        val tarballPrefix = getTarballPrefix(context)
        val requestURL = context.request.requestURL.toString()
        return requestURL.replace(tarballPrefix, remoteConfiguration.url.trimEnd('/'))
    }

    private fun getNodeCreateRequest(context: ArtifactTransferContext, file: ArtifactFile): NodeCreateRequest {
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

    override fun list(context: ArtifactListContext): NpmSearchResponse {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = generateRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            NpmSearchResponse(GsonUtils.gsonToMaps<Any>(response.body()!!.string())?.get(OBJECTS) as MutableList<Map<String, Any>>)
        } else NpmSearchResponse()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmRemoteRepository::class.java)
    }
}
