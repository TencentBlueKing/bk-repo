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
import com.tencent.bkrepo.cargo.pojo.CargoSearchResult
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.CARGO_PREFIX
import com.tencent.bkrepo.cargo.pojo.index.IndexConfiguration
import com.tencent.bkrepo.cargo.utils.ObjectBuilderUtil
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class CargoRemoteRepository : RemoteRepository() {

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
        val downloadUrl = remoteUrl.trim('/') + context.artifactInfo.getArtifactName()
        return executeRequest(context, downloadUrl) { response -> onResponse(context, response) }
    }

    private fun handleCrateFileRequest(context: ArtifactContext, remoteUrl: String): Any? {
        getCacheArtifactResource(context as ArtifactDownloadContext)?.let { return it }

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

        return executeRequest(context, downloadUrl) { response ->
            onResponse(context, response)
        }
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
        return executeRequest(context, downloadUrl) { response -> onResponse(context, response) }
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

    private fun <T> executeRequest(
        context: ArtifactContext, url: String, responseHandler: (Response) -> T
    ): T {
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
            logger.error("Error occurred while sending request $url", e)
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

    private fun parseConfigResponse(response: Response): IndexConfiguration? {
        return response.body?.byteStream().use {
            JsonUtils.objectMapper.readValue(it, IndexConfiguration::class.java)
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
