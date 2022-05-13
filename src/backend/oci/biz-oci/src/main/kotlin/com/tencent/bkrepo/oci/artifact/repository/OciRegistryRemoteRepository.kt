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

import com.tencent.bkrepo.common.api.constant.HttpHeaders.WWW_AUTHENTICATE
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
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
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.util.okhttp.TokenAuthInterceptor
import com.tencent.bkrepo.common.security.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.oci.constant.BEARER_REALM
import com.tencent.bkrepo.oci.constant.LAST_TAG
import com.tencent.bkrepo.oci.constant.MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.N
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.SCOPE
import com.tencent.bkrepo.oci.constant.SERVICE
import com.tencent.bkrepo.oci.exception.OciForbiddenRequestException
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.auth.BearerToken
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.tags.TagsInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OciRegistryRemoteRepository(
    private val ociOperationService: OciOperationService
) : RemoteRepository() {

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Forbidden to upload chart into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw OciForbiddenRequestException(message)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            val remoteConfiguration = context.getRemoteConfiguration()
            val httpClient = createHttpClient(remoteConfiguration)
            val downloadUrl = createRemoteDownloadUrl(context)
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            val token = checkAuthenticate(response, remoteConfiguration)
            if (!token.isNullOrBlank()) {
                val builder = HttpClientBuilderFactory.create()
                val httpClientWithAuth = builder
                    .readTimeout(remoteConfiguration.network.readTimeout, TimeUnit.MILLISECONDS)
                    .connectTimeout(remoteConfiguration.network.connectTimeout, TimeUnit.MILLISECONDS)
                    .addInterceptor(TokenAuthInterceptor(token))
                    .build()
                val requestWithAuth = Request.Builder().url(downloadUrl)
                    .build()
                val responseWithAuth = httpClientWithAuth.newCall(requestWithAuth).execute()
                return if (checkResponse(responseWithAuth)) {
                    onDownloadResponse(context, responseWithAuth)
                } else null
            }
            return if (checkResponse(response)) {
                onDownloadResponse(context, response)
            } else null
        }
    }

    /**
     * 生成远程构件下载url
     */
    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val configuration = context.getRemoteConfiguration()
        val (fullPath, params) = when (context.artifactInfo) {
            is OciBlobArtifactInfo -> {
                with(context.artifactInfo as OciBlobArtifactInfo) {
                    Pair(OciLocationUtils.blobPathLocation(this.getDigest(), this), StringPool.EMPTY)
                }
            }
            is OciManifestArtifactInfo -> {
                with(context.artifactInfo as OciManifestArtifactInfo) {
                    Pair(OciLocationUtils.manifestPathLocation(this.reference, this), StringPool.EMPTY)
                }
            }
            is OciTagArtifactInfo -> {
                createParamsForTagList(context)
            }
            else -> Pair(null, null)
        }
        val baseUrl = URL(configuration.url)
        val v2Url = URL(baseUrl, "/v2" + baseUrl.path)
        return UrlFormatter.format(v2Url.toString(), fullPath, params)
    }

    private fun checkAuthenticate(response: Response, remoteConfiguration: RemoteConfiguration): String? {
        if (response.isSuccessful || response.code() != HttpStatus.UNAUTHORIZED.value) {
            return null
        }
        val wwwAuthenticate = response.header(WWW_AUTHENTICATE)
        if (wwwAuthenticate.isNullOrBlank() || !wwwAuthenticate.startsWith(BEARER_AUTH_PREFIX)) {
            return null
        }
        val url = parseWWWAuthenticateHeader(wwwAuthenticate)
        logger.info("The url for authenticating is $url")
        url?.let {
            val builder = HttpClientBuilderFactory.create()
            val httpClientWithAuth = builder.readTimeout(remoteConfiguration.network.readTimeout, TimeUnit.MILLISECONDS)
                .connectTimeout(remoteConfiguration.network.connectTimeout, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder().url(it).build()
            val tokenResponse = httpClientWithAuth.newCall(request).execute()
            return if (!tokenResponse.isSuccessful) {
                null
            } else {
                val body = tokenResponse.body()!!
                val artifactFile = createTempFile(body)
                val size = artifactFile.getSize()
                val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
                artifactFile.delete()
                val bearerToken = JsonUtils.objectMapper.readValue(artifactStream, BearerToken::class.java)
                "Bearer ${bearerToken.token}"
            }
        }
        return null
    }

    /**
     * 解析返回头中的WWW_AUTHENTICATE字段， 只针对为Bearer realm
     */
    private fun parseWWWAuthenticateHeader(wwwAuthenticate: String): String? {
        val map: MutableMap<String, String> = mutableMapOf()
        return try {
            val params = wwwAuthenticate.split(",")
            params.forEach {
                val param = it.split("=")
                val name = param.first()
                val value = param.last().removeSurrounding("\"")
                map[name] = value
            }
            "${map[BEARER_REALM]}?$SERVICE=${map[SERVICE]}&$SCOPE=${map[SCOPE]}"
        } catch (e: Exception) {
            logger.warn("Parsing wwwAuthenticate header error: ${e.message}")
            null
        }
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
        val artifactFile = createTempFile(response.body()!!)
        val node = cacheArtifact(context, artifactFile)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
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
     * 加载要返回的资源
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
        val configuration = context.getRemoteConfiguration()
        return if (configuration.cache.enabled) {
            val node = when (context.artifactInfo) {
                is OciBlobArtifactInfo -> {
                    ociOperationService.storeArtifact(
                        ociArtifactInfo = context.artifactInfo as OciBlobArtifactInfo,
                        artifactFile = artifactFile,
                        storageCredentials = context.storageCredentials
                    )
                }
                is OciManifestArtifactInfo -> {
                    ociOperationService.storeManifestArtifact(
                        ociArtifactInfo = context.artifactInfo as OciManifestArtifactInfo,
                        artifactFile = artifactFile,
                        storageCredentials = context.storageCredentials
                    )
                    updateManifestAndBlob(context, artifactFile)
                    nodeClient.getNodeDetail(
                        projectId = context.artifactInfo.projectId,
                        repoName = context.artifactInfo.repoName,
                        fullPath = context.artifactInfo.getArtifactFullPath()
                    ).data
                }
                else -> {
                    null
                }
            }
            node
        } else null
    }

    private fun updateManifestAndBlob(context: ArtifactDownloadContext, artifactFile: ArtifactFile) {
        with(context.artifactInfo as OciManifestArtifactInfo) {
            val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
            // 上传manifest文件，同时需要将manifest中对应blob的属性进行补充到blob节点中，同时创建package相关信息
            ociOperationService.updateOciInfo(
                ociArtifactInfo = this,
                digest = digest,
                artifactFile = artifactFile,
                fullPath = context.artifactInfo.getArtifactFullPath(),
                storageCredentials = context.storageCredentials
            )
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        val body = response.body()!!
        val artifactFile = createTempFile(body)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        artifactFile.delete()
        return JsonUtils.objectMapper.readValue(artifactStream, TagsInfo::class.java)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(OciRegistryRemoteRepository::class.java)
    }
}
