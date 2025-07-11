/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.cargo.artifact.repository

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.cargo.constants.CRATE_CONFIG
import com.tencent.bkrepo.cargo.constants.CRATE_FILE
import com.tencent.bkrepo.cargo.constants.CRATE_INDEX
import com.tencent.bkrepo.cargo.constants.CRATE_NAME
import com.tencent.bkrepo.cargo.constants.CRATE_VERSION
import com.tencent.bkrepo.cargo.constants.CargoMessageCode
import com.tencent.bkrepo.cargo.constants.FILE_TYPE
import com.tencent.bkrepo.cargo.constants.PAGE_SIZE
import com.tencent.bkrepo.cargo.constants.QUERY
import com.tencent.bkrepo.cargo.exception.CargoBadRequestException
import com.tencent.bkrepo.cargo.listener.event.CargoPackageDeleteEvent
import com.tencent.bkrepo.cargo.pojo.CargoSearchResult
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.CARGO_PREFIX
import com.tencent.bkrepo.cargo.pojo.artifact.CargoDeleteArtifactInfo
import com.tencent.bkrepo.cargo.pojo.event.CargoPackageDeleteRequest
import com.tencent.bkrepo.cargo.pojo.index.IndexConfiguration
import com.tencent.bkrepo.cargo.service.impl.CommonService
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.text.ifEmpty

@Component
class CargoRemoteRepository(
    private val commonService: CommonService,
) : RemoteRepository() {

    private val downloadHostCache = CacheBuilder.newBuilder().maximumSize(100)
        .expireAfterWrite(60, TimeUnit.MINUTES).build<String, String>()

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Forbidden to upload crate into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw CargoBadRequestException(CargoMessageCode.CARGO_FILE_UPLOAD_FORBIDDEN, "$projectId/$repoName")
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        return doRequest(context)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return doRequest(context) as ArtifactResource?
    }

    override fun isExpiredForNonPositiveValue() = true

    private fun doRequest(context: ArtifactContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        val remoteUrl = remoteConfiguration.url
        if (remoteUrl.isEmpty()) {
            logger.warn("cargo remoteUrl is empty")
            throw CargoBadRequestException(CommonMessageCode.PARAMETER_INVALID, "remoteUrl")
        }
        return when (context.getStringAttribute(FILE_TYPE)) {
            CRATE_INDEX -> handleIndexFileRequest(context, remoteUrl)
            CRATE_FILE -> {
                val artifactResource = handleCrateFileRequest(context, remoteUrl)
                if (artifactResource != null) {
                    initVersion(context, artifactResource as ArtifactResource)
                }
                return artifactResource
            }

            else -> handleSearchRequest(context, remoteUrl)
        }
    }

    private fun handleIndexFileRequest(context: ArtifactContext, remoteUrl: String): Any? {
        require(context is ArtifactDownloadContext)
        val downloadUrl = remoteUrl.trim('/') + context.artifactInfo.getArtifactName()
        // 索引文件内容可能会更新，优先级：未过期缓存 > 网络请求 > 已过期缓存
        val (cacheNode, isExpired) = getCacheInfo(context)
            // 缓存不存在时以网络请求为最终结果
            ?: return executeRequest(context, downloadUrl)
        if (isExpired) {
            // 缓存过期时先尝试网络请求，如果网络请求出错，则忽略错误并继续使用已过期的缓存
            try {
                executeRequest(context, downloadUrl)?.let { return it }
            } catch (e: Exception) {
                logger.warn("falling back to cache due to failed network request for the crate index.", e)
            }
        }
        return loadArtifactResource(cacheNode, context)
    }

    private fun handleCrateFileRequest(context: ArtifactContext, remoteUrl: String): Any? {
        // crate文件不会变化，优先获取缓存
        getCacheInfo(context)?.let { return loadArtifactResource(it.first, context) }

        val downloadHost = getDownloadHost(remoteUrl, context)
        val artifactInfo = context.artifactInfo as CargoArtifactInfo
        val downloadUrl = buildString {
            append(downloadHost.trimEnd('/'))
            append(CARGO_PREFIX)
            append(StringPool.SLASH)
            append(artifactInfo.crateName)
            append(StringPool.SLASH)
            append(artifactInfo.crateVersion)
            append(StringPool.SLASH)
            append("download")
        }

        return executeRequest(context, downloadUrl)
    }

    private fun initVersion(context: ArtifactContext, artifactResource: ArtifactResource) {
        with(context as ArtifactDownloadContext) {
            val packageVersionCreateRequest = ObjectBuilderUtil.buildPackageVersionCreateRequest(
                userId = userId,
                projectId = projectId,
                repoName = repoName,
                name = getStringAttribute(CRATE_NAME)!!,
                version = getStringAttribute(CRATE_VERSION)!!,
                size = artifactResource.node!!.size,
                fullPath = artifactResource.node!!.fullPath,
            )
            packageService.createPackageVersion(packageVersionCreateRequest).apply {
                logger.info("user: [$userId] create remote package version [$packageVersionCreateRequest] success!")
            }
        }
    }

    private fun handleSearchRequest(context: ArtifactContext, remoteUrl: String): Any? {
        val downloadHost = getDownloadHost(remoteUrl, context)
        val downloadUrl = buildString {
            append(downloadHost.trimEnd('/'))
            append(CARGO_PREFIX)
            append("?q=")
            append(context.getStringAttribute(QUERY))
            append("&per_page=")
            append(context.getIntegerAttribute(PAGE_SIZE))
        }
        return executeRequest(context, downloadUrl)
    }

    private fun getDownloadHost(remoteUrl: String, context: ArtifactContext): String {
        return downloadHostCache.getIfPresent(remoteUrl) ?: run {
            val configUrl = remoteUrl.trim('/') + StringPool.SLASH + CRATE_CONFIG
            val config = executeRequest(context, configUrl) { response ->
                if (checkResponse(response)) {
                    parseConfigResponse(response)
                } else {
                    throw CargoBadRequestException(CommonMessageCode.PARAMETER_INVALID, "remoteUrl")
                }
            } as IndexConfiguration

            downloadHostCache.put(remoteUrl, config.api)
            config.api
        }
    }

    private fun executeRequest(
        context: ArtifactContext,
        url: String,
        responseHandler: (Response) -> Any? = { response -> onResponse(context, response) }
    ): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        val request = Request.Builder().url(url).build()

        val httpClient = createHttpClient(remoteConfiguration, followRedirect = true)

        return try {
            logger.info("Sending cargo request $url")
            httpClient.newCall(request).execute().use {
                if (checkResponse(it)) {
                    responseHandler(it)
                } else {
                    throw CargoBadRequestException(CommonMessageCode.PARAMETER_INVALID, "remoteUrl")
                }
            }
        } catch (e: Exception) {
            logger.warn("Error occurred while sending request $url", e)
            throw NodeNotFoundException(context.artifactInfo.getArtifactFullPath())
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        logger.info("on remote query response...")
        return parseSearchResponse(response)
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ) = commonService.buildDownloadRecord(context.userId, context.artifactInfo as CargoArtifactInfo)

    private fun parseConfigResponse(response: Response): IndexConfiguration? {
        return response.body?.byteStream().use {
            JsonUtils.objectMapper.readValue(it, IndexConfiguration::class.java)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        commonService.removeCargoRelatedNode(context)
        with(context.artifactInfo as CargoDeleteArtifactInfo) {
            val event = CargoPackageDeleteEvent(
                CargoPackageDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    name = PackageKeys.resolveCargo(packageName),
                    userId = context.userId,
                    version = version.ifEmpty { null }
                )
            )
            publishEvent(event)
        }
    }

    private fun parseSearchResponse(response: Response): CargoSearchResult {
        return response.body?.byteStream().use {
            JsonUtils.objectMapper.readValue(it, CargoSearchResult::class.java)
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

    companion object {
        val logger: Logger = LoggerFactory.getLogger(CargoRemoteRepository::class.java)
    }
}
