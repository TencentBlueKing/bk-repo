package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_PKG_VERSION_METADATA_FULL_PATH
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import io.undertow.util.BadRequestException
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream

@Component
class NpmRemoteRepository : RemoteRepository() {

    @Value("\${npm.tarball.prefix}")
    private val tarballPrefix: String = StringPool.SLASH

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        getCacheArtifactResource(context)?.let { return it }
        val tgzFile = super.onDownload(context)
        installPkgVersionFile(context)
        return tgzFile
    }

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val remoteConfiguration = context.getRemoteConfiguration()
        val tarballPrefix = getTarballPrefix(context)
        val queryString = context.request.queryString
        val requestURL =
            context.request.requestURL.toString() + if (StringUtils.isNotEmpty(queryString)) "?$queryString" else ""
        context.putAttribute(NPM_FILE_FULL_PATH, requestURL.removePrefix(tarballPrefix))
        return requestURL.replace(tarballPrefix, remoteConfiguration.url.trimEnd('/'))
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val nodeCreateRequest = super.buildCacheNodeCreateRequest(context, artifactFile)
        return nodeCreateRequest.copy(
            fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        )
    }

    private fun getCacheArtifactResource(context: ArtifactContext): ArtifactResource? {
        val remoteConfiguration = context.getRemoteConfiguration()
        if (!remoteConfiguration.cache.enabled) return null
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val cacheNode = nodeClient.detail(context.projectId, context.repoName, fullPath).data
        if (cacheNode == null || cacheNode.folder) return null
        return if (!isExpired(cacheNode, remoteConfiguration.cache.expiration)) {
            storageService.load(
                cacheNode.sha256!!, Range.full(cacheNode.size),
                context.storageCredentials
            )?.run {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit")
                ArtifactResource(this, context.artifactInfo.getResponseName(), cacheNode, ArtifactChannel.LOCAL, true)
            }
        } else null
    }

    /**
     * install pkg-version json file when download tgzFile
     */
    fun installPkgVersionFile(context: ArtifactDownloadContext) {
        val tgzFullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val pkgInfo = parseArtifactInfo(tgzFullPath)
        context.putAttribute(NPM_FILE_FULL_PATH,
            String.format(NPM_PKG_METADATA_FULL_PATH, pkgInfo.first))
        try {
            val artifactResource = getCacheArtifactResource(context) ?: return
            val jsonFile = transFileToJson(artifactResource.inputStream, context)
            val versionFile = jsonFile.getAsJsonObject(VERSIONS).getAsJsonObject(pkgInfo.second)
            val artifact = ArtifactFileFactory.build(GsonUtils.gsonToInputStream(versionFile))
            val name = jsonFile[NAME].asString
            context.putAttribute(NPM_FILE_FULL_PATH,
                String.format(NPM_PKG_VERSION_METADATA_FULL_PATH, name, name, pkgInfo.second))
            cacheArtifactFile(context, artifact)
        } catch (ex: TypeCastException) {
            logger.warn("cache artifact [${pkgInfo.first}-${pkgInfo.second}.json] failed, {}", ex.message)
        }
    }

    private fun parseArtifactInfo(tgzFullPath: String): Pair<String, String> {
        val pkgList = tgzFullPath.split('/').filter { it.isNotBlank() }.map { it.trim() }.toList()
        var pkgName = pkgList[0]
        if (pkgList[1].contains('@')) {
            pkgName = pkgList[0] + pkgList[1]
        }
        val version = pkgList.last().substringAfterLast('-').substringBeforeLast(".tgz")
        return Pair(pkgName, version)
    }

    override fun query(context: ArtifactQueryContext): JsonObject? {
        getCacheArtifactResource(context)?.let {
            return transFileToJson(it.inputStream, context)
        }
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = createHttpClient(remoteConfiguration)
        val searchUri = generateRemoteSearchUrl(context)
        val request = Request.Builder().url(searchUri).build()
        var response: Response? = null
        return try {
            response = httpClient.newCall(request).execute()
            if (checkResponse(response)) {
                val file = createTempFile(response.body()!!)
                val resultJson = transFileToJson(file.getInputStream(), context)
                cacheArtifactFile(context, file)
                resultJson
            } else null
        } catch (exception: IOException) {
            logger.error("http send [$searchUri] failed, {}", exception)
            throw exception
        } finally {
            if (response != null) {
                response.body()?.close()
            }
        }
    }

    private fun transFileToJson(inputStream: InputStream, context: ArtifactContext): JsonObject {
        val pkgJson = GsonUtils.transferInputStreamToJson(inputStream)
        val name = pkgJson.get(NAME).asString
        val id = pkgJson[ID].asString
        if (id.substring(1).contains('@')) {
            val oldTarball = pkgJson.getAsJsonObject(DIST)[TARBALL].asString
            pkgJson.getAsJsonObject(DIST).addProperty(
                TARBALL,
                NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name, context.artifactInfo)
            )
        } else {
            val versions = pkgJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val versionObject = versions.getAsJsonObject(it)
                val oldTarball = versionObject.getAsJsonObject(DIST)[TARBALL].asString
                versionObject.getAsJsonObject(DIST).addProperty(
                    TARBALL,
                    NpmUtils.buildPackageTgzTarball(oldTarball, tarballPrefix, name, context.artifactInfo)
                )
            }
        }
        return pkgJson
    }

    private fun getTarballPrefix(context: ArtifactContext): String {
        val requestURL = context.request.requestURL.toString()
        val requestURI = context.request.requestURI
        val projectId = context.artifactInfo.projectId
        val repoName = context.artifactInfo.repoName
        val replace = requestURL.replace(requestURI, "")
        return "$replace/$projectId/$repoName"
    }

    private fun generateRemoteSearchUrl(context: ArtifactQueryContext): String {
        val remoteConfiguration = context.getRemoteConfiguration()
        val tarballPrefix = getTarballPrefix(context)
        val requestURL = context.request.requestURL.toString()
        return requestURL.replace(tarballPrefix, remoteConfiguration.url.trimEnd('/'))
    }

    override fun search(context: ArtifactSearchContext): List<NpmSearchInfoMap> {
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = createRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            val npmSearchResponse = JsonUtils.objectMapper.readValue(response.body()!!.byteStream(), NpmSearchResponse::class.java)
            npmSearchResponse.objects
        } else emptyList()
    }

    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        logger.warn("Unable to migrate npm package into a remote repository")
        throw BadRequestException("Unable to migrate npm package into a remote repository")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmRemoteRepository::class.java)
    }
}
