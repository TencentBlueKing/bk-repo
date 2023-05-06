/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpHeaders.WWW_AUTHENTICATE
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.http.AuthenticationUtil.addProtocol
import com.tencent.bkrepo.common.artifact.util.http.AuthenticationUtil.buildAuthenticationUrl
import com.tencent.bkrepo.common.artifact.util.http.AuthenticationUtil.parseWWWAuthenticateHeader
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.oci.constant.DOCKER_DISTRIBUTION_MANIFEST_V2
import com.tencent.bkrepo.oci.constant.DOCKER_LINK
import com.tencent.bkrepo.oci.constant.LAST_TAG
import com.tencent.bkrepo.oci.constant.MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.N
import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.OciMessageCode
import com.tencent.bkrepo.oci.constant.PROXY_URL
import com.tencent.bkrepo.oci.constant.REQUEST_TAG_LIST
import com.tencent.bkrepo.oci.exception.OciForbiddenRequestException
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo.Companion.DOCKER_CATALOG_SUFFIX
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.auth.BearerToken
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.remote.RemoteUrlProperty
import com.tencent.bkrepo.oci.pojo.response.CatalogResponse
import com.tencent.bkrepo.oci.pojo.response.OciResponse
import com.tencent.bkrepo.oci.pojo.tags.TagsInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.ws.rs.core.UriBuilder

@Component
class OciRegistryRemoteRepository(
    private val ociOperationService: OciOperationService
) : RemoteRepository() {

    private val clientCache = ConcurrentHashMap<RemoteConfiguration, OkHttpClient>()
    private val tokenCache = ConcurrentHashMap<String, String>()

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Forbidden to upload chart into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw OciForbiddenRequestException(OciMessageCode.OCI_FILE_UPLOAD_FORBIDDEN, "$projectId|$repoName")
        }
    }

    /**
     * 下载制品
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            return doRequest(context) as ArtifactResource?
        }
    }

    /**
     * query功能主要用于
     * 1.获取对应tag列
     * 2.获取manifest文件内容
     */
    override fun query(context: ArtifactQueryContext): Any? {
        return doRequest(context)
    }

    /**
     * 注意：针对oci仓库配置的username/password鉴权方式请求返回401，需要额外去获取token
     */
    private fun doRequest(context: ArtifactContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = clientCache.getOrPut(remoteConfiguration) {
            createHttpClient(remoteConfiguration, false)
        }
        val property = getRemoteUrlProperty(context)
        val downloadUrl = createRemoteDownloadUrl(context, property)
        logger.info("Remote request $downloadUrl will be sent")
        val tokenKey = buildTokenCacheKey(remoteConfiguration.url, property.imageName)
        val request = buildRequest(downloadUrl, remoteConfiguration, tokenCache[tokenKey])
        val response = httpClient.newCall(request).execute()
        var responseWithAuth: Response? = null
        try {
            if (response.isSuccessful) return onResponse(context, response)
            // 针对返回401进行token获取
            val token = getAuthenticationCode(context, response, remoteConfiguration, property.imageName)
            if (token.isNullOrBlank()) return null
            tokenCache[tokenKey] = token
            val requestWithAuth = buildRequest(
                url = downloadUrl,
                configuration = remoteConfiguration,
                addBasicInterceptor = false,
                token = token
            )
            responseWithAuth = httpClient
                .newCall(requestWithAuth)
                .execute()
            return if (checkResponse(responseWithAuth)) {
                onResponse(context, responseWithAuth)
            } else null
        } finally {
            response.body?.close()
            responseWithAuth?.body?.close()
        }
    }

    private fun onResponse(context: ArtifactContext, response: Response): Any? {
        if (context is ArtifactDownloadContext) {
            return onDownloadResponse(context, response)
        }
        if (context is ArtifactQueryContext) {
            return onQueryResponse(context, response)
        }
        return null
    }

    /**
     * 默认情况下进行basic认证， 如basic认证返回401，则进行token认证
     */
    private fun buildRequest(
        url: String,
        configuration: RemoteConfiguration,
        token: String? = null,
        addBasicInterceptor: Boolean = true
    ): Request {
        val requestBuilder = Request.Builder().url(url)
        if (addBasicInterceptor && token.isNullOrEmpty()) {
            requestBuilder.addInterceptor(configuration)
        } else {
            token?.let { requestBuilder.header(HttpHeaders.AUTHORIZATION, token) }
        }
        if (url.contains("/manifests/")) {
            requestBuilder.header(HttpHeaders.ACCEPT, DOCKER_DISTRIBUTION_MANIFEST_V2)
        }
        return requestBuilder.build()
    }

    private fun Request.Builder.addInterceptor(configuration: RemoteConfiguration): Request.Builder {
        val username = configuration.credentials.username
        val password = configuration.credentials.password
        if (username != null && password != null) {
            val credentials =  BasicAuthUtils.encode(username, password)
            this.header(HttpHeaders.AUTHORIZATION, credentials)
        }
        return this
    }

    /**
     * 生成远程构件下载url
     */
    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val property = getRemoteUrlProperty(context)
        return createRemoteDownloadUrl(context, property)
    }

    fun createRemoteDownloadUrl(context: ArtifactContext, property: RemoteUrlProperty): String {
        return when (property.type) {
            REQUEST_TAG_LIST -> createCatalogUrl(property)
            else -> createUrl(property)
        }
    }

    private fun getRemoteUrlProperty(context: ArtifactContext): RemoteUrlProperty {
        val configuration = context.getRemoteConfiguration()
        val url = addProtocol(configuration.url).toString()
        context.putAttribute(PROXY_URL, url)
        if (context.artifactInfo is OciBlobArtifactInfo) {
            val artifactInfo = context.artifactInfo as OciBlobArtifactInfo
            return RemoteUrlProperty(
                url = url,
                fullPath = OciLocationUtils.blobPathLocation(artifactInfo.getDigest(), artifactInfo),
                imageName = artifactInfo.packageName
            )
        }
        if (context.artifactInfo is OciManifestArtifactInfo) {
            val artifactInfo = context.artifactInfo as OciManifestArtifactInfo
            return RemoteUrlProperty(
                url = url,
                fullPath = OciLocationUtils.manifestPathLocation(artifactInfo.reference, artifactInfo),
                imageName = artifactInfo.packageName
            )
        }
        if (context.artifactInfo is OciTagArtifactInfo) {
            val artifactInfo = context.artifactInfo as OciTagArtifactInfo
            if (artifactInfo.packageName.isBlank()) {
                val (_, params) = createParamsForTagList(context)
                return RemoteUrlProperty(
                    url = url,
                    params = params,
                    type = REQUEST_TAG_LIST,
                    imageName = StringPool.EMPTY
                )
            } else {
                val (fullPath, params) = createParamsForTagList(context)
                return RemoteUrlProperty(
                    url = url,
                    fullPath = fullPath,
                    params = params,
                    imageName = artifactInfo.packageName
                    )
            }
        }
        return RemoteUrlProperty(url = url, imageName = StringPool.EMPTY)
    }

    /**
     * 拼接url
     */
    private fun createUrl(property: RemoteUrlProperty): String {
        with(property) {
            val baseUrl = URL(url)
            val v2Url = URL(baseUrl, "/v2" + baseUrl.path)
            return UrlFormatter.format(v2Url.toString(), fullPath, params)
        }
    }

    /**
     * 拼接catalog url
     */
    private fun createCatalogUrl(property: RemoteUrlProperty): String {
        with(property) {
            val baseUrl = URL(url)
            val builder = UriBuilder.fromPath(OCI_API_PREFIX)
                .host(baseUrl.host).scheme(baseUrl.protocol)
                .path(DOCKER_CATALOG_SUFFIX)
                .queryParam(params)
            return builder.build().toString()
        }
    }

    private fun getAuthenticationCode(
        context: ArtifactContext,
        response: Response,
        configuration: RemoteConfiguration,
        imageName: String
    ): String? {
        if (response.code != HttpStatus.UNAUTHORIZED.value) {
            return null
        }
        val wwwAuthenticate = response.header(WWW_AUTHENTICATE)
        if (wwwAuthenticate.isNullOrBlank() || !wwwAuthenticate.startsWith(BEARER_AUTH_PREFIX)) {
            return null
        }
        val url = context.getStringAttribute(PROXY_URL)!!
        val scope = getScope(url, imageName)
        val authProperty = parseWWWAuthenticateHeader(wwwAuthenticate, scope)
        if (authProperty == null)  {
            logger.warn("Auth url can not be parsed from header!")
            return null
        }
        val urlStr = buildAuthenticationUrl(authProperty, configuration.credentials.username)
        logger.info("The url for authenticating is $urlStr")
        if (urlStr.isNullOrEmpty()) return null
        val request = buildRequest(
            url = urlStr,
            configuration = configuration,
            addBasicInterceptor = true
        )
        clientCache[configuration]!!.newCall(request).execute().use {
            if (!it.isSuccessful) {
                val error = JsonUtils.objectMapper.readValue(it.body!!.byteStream(), OciResponse::class.java)
                logger.warn(
                    "Could not get token from auth service," +
                        " code is ${it.code} and response is ${error.toJsonString()}"
                )
                return null
            }
            val bearerToken = JsonUtils.objectMapper.readValue(it.body!!.byteStream(), BearerToken::class.java)
            return "Bearer ${bearerToken.token}"
        }

    }

    private fun getScope(remoteUrl: String, imageName: String): String {
        val baseUrl = URL(remoteUrl)
        val target = baseUrl.path.removePrefix(StringPool.SLASH)
            .removeSuffix(StringPool.SLASH) + StringPool.SLASH + imageName
        return "repository:$target:pull"
    }

    private fun createParamsForTagList(context: ArtifactContext): Pair<String, String> {
        with(context.artifactInfo as OciTagArtifactInfo) {
            val n = context.getAttribute<Int>(N)
            val last = context.getAttribute<String>(LAST_TAG)
            var param = StringPool.EMPTY
            if (n != null) {
                param += "n=$n"
                if (!last.isNullOrBlank()) {
                    param += "&last=$last"
                }
            } else {
                if (!last.isNullOrBlank()) {
                    param += "last=$last"
                }
            }
            return Pair("/$packageName/tags/list", param)
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onDownloadResponse(context: ArtifactDownloadContext, response: Response): ArtifactResource {
        logger.info("Remote download response will be processed")
        val artifactFile = createTempFile(response.body!!)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val node = cacheArtifact(context, artifactFile)
        val artifactResource = ArtifactResource(
            inputStream = artifactStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = node,
            channel = ArtifactChannel.LOCAL
        )
        return buildResponse(
            cacheNode = node,
            context = context,
            artifactResource = artifactResource,
            sha256 = artifactFile.getFileSha256(),
            size = size
        )
    }

    /**
     * 尝试获取缓存的远程构件节点
     */
    override fun findCacheNodeDetail(context: ArtifactDownloadContext): NodeDetail? {
        with(context) {
            val fullPath = ociOperationService.getNodeFullPath(context.artifactInfo as OciArtifactInfo) ?: return null
            return nodeClient.getNodeDetail(projectId, repoName, fullPath).data
        }
    }

    /**
     * 加载要返回的资源: oci协议需要返回特定的请求头和资源类型
     */
    override fun loadArtifactResource(cacheNode: NodeDetail, context: ArtifactDownloadContext): ArtifactResource? {
        return storageService.load(cacheNode.sha256!!, Range.full(cacheNode.size), context.storageCredentials)?.run {
            if (logger.isDebugEnabled) {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit.")
            }
            val artifactResource = ArtifactResource(
                inputStream = this,
                artifactName = context.artifactInfo.getResponseName(),
                node = cacheNode,
                channel = ArtifactChannel.PROXY
            )
            buildResponse(
                cacheNode = cacheNode,
                context = context,
                artifactResource = artifactResource
            )
        }
    }

    private fun buildResponse(
        cacheNode: NodeDetail?,
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        sha256: String? = null,
        size: Long? = null
    ): ArtifactResource {
        val digest = if (cacheNode != null) {
            OciDigest.fromSha256(cacheNode.sha256!!)
        } else {
            OciDigest.fromSha256(sha256!!)
        }
        val length = cacheNode?.size ?: size
        val mediaType = cacheNode?.metadata?.get(MEDIA_TYPE) ?: MediaTypes.APPLICATION_OCTET_STREAM
        val contentType = if (context.artifactInfo is OciManifestArtifactInfo) {
            cacheNode?.metadata?.get(MEDIA_TYPE) ?: OCI_IMAGE_MANIFEST_MEDIA_TYPE
        } else {
            MediaTypes.APPLICATION_OCTET_STREAM
        }
        OciResponseUtils.buildDownloadResponse(
            digest = digest,
            response = context.response,
            size = length,
            contentType = contentType.toString()
        )
        artifactResource.contentType = mediaType.toString()
        return artifactResource
    }

    fun cacheArtifact(context: ArtifactDownloadContext, artifactFile: ArtifactFile): NodeDetail? {
        logger.info("Remote artifact will be cached")
        val configuration = context.getRemoteConfiguration()
        if (!configuration.cache.enabled) return null
        val ociArtifactInfo = context.artifactInfo as OciArtifactInfo
        val fullPath = ociOperationService.getNodeFullPath(ociArtifactInfo)
        // 针对manifest文件获取会通过tag或manifest获取，避免重复创建
        fullPath?.let {
            val node = nodeClient.getNodeDetail(ociArtifactInfo.projectId, ociArtifactInfo.repoName, fullPath).data
            if (node != null) return node
        }
        val url = context.getStringAttribute(PROXY_URL)
        var nodeDetail = ociOperationService.storeArtifact(
            ociArtifactInfo = ociArtifactInfo,
            artifactFile = artifactFile,
            storageCredentials = context.storageCredentials,
            proxyUrl = url
        )
        // 针对manifest文件需要更新metadata
        if (context.artifactInfo is OciManifestArtifactInfo) {
            updateManifestAndBlob(context, nodeDetail!!)
            nodeDetail = nodeClient.getNodeDetail(
                projectId = context.artifactInfo.projectId,
                repoName = context.artifactInfo.repoName,
                fullPath = context.artifactInfo.getArtifactFullPath()
            ).data
        }
        return nodeDetail
    }

    private fun updateManifestAndBlob(context: ArtifactDownloadContext, nodeDetail: NodeDetail) {
        with(context.artifactInfo as OciManifestArtifactInfo) {
            val digest = OciDigest.fromSha256(nodeDetail.sha256!!)
            // 上传manifest文件，同时需要将manifest中对应blob的属性进行补充到blob节点中，同时创建package相关信息
            ociOperationService.updateOciInfo(
                ociArtifactInfo = this,
                digest = digest,
                nodeDetail = nodeDetail,
                storageCredentials = context.storageCredentials,
                sourceType = ArtifactChannel.PROXY
            )
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        val body = response.body!!
        val artifactFile = createTempFile(body)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        artifactFile.delete()
        return if (context.artifactInfo is OciTagArtifactInfo) {
            val link = response.header(DOCKER_LINK)
            val left = parseLink(link)
            if ((context.artifactInfo as OciTagArtifactInfo).packageName.isNotBlank()) {
                convertTagsInfo(artifactStream, left)
            } else {
                convertCatalogInfo(artifactStream, left)
            }
        } else {
            artifactStream
        }
    }

    // 获取tag列表
    private fun convertTagsInfo(artifactStream: InputStream, left: Int): TagsInfo? {
        val tags = JsonUtils.objectMapper.readValue(artifactStream, TagsInfo::class.java)
        tags.left = left
        return tags
    }

    // 获取catalog列表
    private fun convertCatalogInfo(artifactStream: InputStream, left: Int): CatalogResponse? {
        val catalog = JsonUtils.objectMapper.readValue(artifactStream, CatalogResponse::class.java)
        catalog.left = left
        return catalog
    }

    /**
     * 针对返回头中link字段进行解析
     * Link: <<url>?n=<last n value>&last=<last entry from response>>; rel="next"
     */
    private fun parseLink(link: String?): Int {
        if (link.isNullOrBlank()) return 0
        var n = 0
        try {
            val regex = "<(.*)>; *rel=\"(.*)\""
            val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)
            val matcher = pattern.matcher(link)
            val linkUrl = if (matcher.find()) {
                matcher.group(1)
            } else null
            val query = linkUrl?.split("?")?.last()
            val map = OciResponseUtils.parseQuerystring(query)
            n = map?.get("n")?.toInt() ?: 0
            return n
        } catch (ignore: Exception) {
            logger.warn("Error occurred while parsing linker, ${ignore.message}")
        }
        return n
    }

    private fun buildTokenCacheKey(remoteUrl: String, imageName: String): String {
        val scope = getScope(remoteUrl, imageName)
        return "$remoteUrl${CharPool.COLON}$scope"
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(OciRegistryRemoteRepository::class.java)
    }
}
