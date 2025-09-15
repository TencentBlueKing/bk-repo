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

package com.tencent.bkrepo.common.artifact.repository.remote

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.useCache
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils.resolveContentRange
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod.HEAD
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 远程仓库抽象逻辑
 */
@Suppress("TooManyFunctions")
abstract class RemoteRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var asyncCacheWriter: AsyncRemoteArtifactCacheWriter

    @Autowired
    lateinit var storageProperties: StorageProperties

    @Autowired
    lateinit var storageHealthMonitorHelper: StorageHealthMonitorHelper

    @Autowired
    lateinit var cacheLocks: RemoteArtifactCacheLocks

    @Autowired
    lateinit var metadataService: MetadataService

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            val remoteConfiguration = context.getRemoteConfiguration()
            val httpClient = createHttpClient(remoteConfiguration)
            val downloadUrl = createRemoteDownloadUrl(context)
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            return if (checkResponse(response)) {
                onDownloadResponse(context, response)
            } else {
                response.close()
                null
            }
        }
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = createRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            onSearchResponse(context, response)
        } else emptyList()
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = createRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (checkQueryResponse(response)) {
                onQueryResponse(context, response)
            } else null
        } catch (e: Exception) {
            logger.warn("Failed to request or resolve response: ${e.message}")
            null
        }
    }

    /**
     * 尝试读取缓存的远程构件
     */
    fun getCacheArtifactResource(context: ArtifactDownloadContext): ArtifactResource? {
        val (cacheNode, isExpired) = getCacheInfo(context) ?: return null
        return if (isExpired) null else loadArtifactResource(cacheNode, context)
    }

    /**
     * 获取缓存的远程构件节点及过期状态
     */
    protected fun getCacheInfo(context: ArtifactContext): Pair<NodeDetail, Boolean>? {
        if (!shouldCache(context)) {
            return null
        }

        val cacheNode = findCacheNodeDetail(context)
        return if (cacheNode == null || cacheNode.folder) null else
            Pair(cacheNode, isExpired(cacheNode, context.getRemoteConfiguration().cache.expiration))
    }

    /**
     * 加载要返回的资源
     */
    open fun loadArtifactResource(cacheNode: NodeDetail, context: ArtifactContext): ArtifactResource? {
        return storageManager.loadFullArtifactInputStream(cacheNode, context.storageCredentials)?.run {
            if (logger.isDebugEnabled) {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit.")
            }
            ArtifactResource(this, context.artifactInfo.getResponseName(), cacheNode, ArtifactChannel.PROXY)
        }
    }

    /**
     * 判断缓存节点[cacheNode]是否过期，[expiration]表示有效期，单位分钟
     */
    protected fun isExpired(cacheNode: NodeDetail, expiration: Long): Boolean {
        if (expiration <= 0) {
            return isExpiredForNonPositiveValue()
        }
        val createdDate = LocalDateTime.parse(cacheNode.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        return Duration.between(createdDate, LocalDateTime.now()).toMinutes() >= expiration
    }

    /**
     * expiration 为0或负数时表示是否过期
     * @see [com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteCacheConfiguration.expiration]
     */
    protected open fun isExpiredForNonPositiveValue(): Boolean = false

    /**
     * 尝试获取缓存的远程构件节点
     */
    open fun findCacheNodeDetail(context: ArtifactContext): NodeDetail? {
        with(context) {
            return nodeService.getNodeDetail(artifactInfo)
        }
    }

    /**
     * 将远程拉取的构件缓存本地
     */
    protected fun cacheArtifactFile(context: ArtifactContext, artifactFile: ArtifactFile): NodeDetail? {
        return if (shouldCache(context)) {
            val nodeCreateRequest = buildCacheNodeCreateRequest(context, artifactFile)
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, context.storageCredentials)
        } else null
    }

    /**
     * 远程下载响应回调
     */
    open fun onDownloadResponse(
        context: ArtifactDownloadContext,
        response: Response,
        useDisposition: Boolean = false,
        syncCache: Boolean = true,
    ): ArtifactResource {
        return if (syncCache) {
            syncCacheResponse(response, context, useDisposition)
        } else {
            asyncCacheResponse(response, context, useDisposition)
        }
    }

    private fun syncCacheResponse(
        response: Response,
        context: ArtifactDownloadContext,
        useDisposition: Boolean,
    ): ArtifactResource {
        val artifactFile = createTempFile(response.body!!)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val node = cacheArtifactFile(context, artifactFile)
        return ArtifactResource(
            artifactStream,
            context.artifactInfo.getResponseName(),
            node,
            ArtifactChannel.LOCAL,
            useDisposition
        )
    }

    private fun asyncCacheResponse(
        response: Response,
        context: ArtifactDownloadContext,
        useDisposition: Boolean,
    ): ArtifactResource {
        if (response.header(HttpHeaders.TRANSFER_ENCODING) == "chunked") {
            throw ErrorCodeException(PARAMETER_INVALID, "Transfer-Encoding: chunked was not supported")
        }
        val contentLength = response.header(HttpHeaders.CONTENT_LENGTH)!!.toLong()
        val contentRange = resolveContentRange(response.header(HttpHeaders.CONTENT_RANGE))
        val range = contentRange ?: Range.full(contentLength)

        val request = HttpContextHolder.getRequestOrNull()
        val artifactStream = if (contentRange?.isEmpty() == true || request?.method == HEAD.name) {
            onEmptyResponse(response, range, context)
        } else {
            // 返回文件内容
            response.body!!.byteStream().artifactStream(range).apply {
                if (range.isFullContent() && shouldCache(context)) {
                    // 仅缓存完整文件，返回响应的同时写入缓存
                    addListener(buildCacheWriter(context, contentLength))
                } else if (shouldCache(context)) {
                    // 分片下载时异步拉取完整文件并缓存
                    asyncCache(context, response.request)
                }
            }
        }
        // 由于此处node为null，需要手动设置sha256,md5等node相关header
        addHeadersOfNode(response.headers)
        return ArtifactResource(
            inputStream = artifactStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = null,
            channel = ArtifactChannel.LOCAL,
            useDisposition = useDisposition,
        )
    }

    protected open fun onEmptyResponse(
        response: Response,
        range: Range,
        context: ArtifactDownloadContext,
    ): ArtifactInputStream {
        // 返回空文件
        response.close()
        return ArtifactInputStream(EmptyInputStream.INSTANCE, range)
    }

    /**
     * 使用此方法可以避免远程Node尚未被缓存时，返回给客户端的响应中不包含Node相关header
     */
    open fun addHeadersOfNode(headers: Headers) {
        val response = HttpContextHolder.getResponse()
        headers[HttpHeaders.ETAG]?.let { response.setHeader(HttpHeaders.ETAG, it) }
    }

    private fun asyncCache(context: ArtifactDownloadContext, request: Request) {
        val repoDetail = context.repositoryDetail
        val remoteNodes = safeSearchParents(context.repositoryDetail, context.artifactInfo)
        val cacheTask = AsyncRemoteArtifactCacheWriter.CacheTask(
            projectId = repoDetail.projectId,
            repoName = repoDetail.name,
            storageCredentials = repoDetail.storageCredentials ?: storageProperties.defaultStorageCredentials(),
            remoteConfiguration = context.getRemoteConfiguration(),
            fullPath = context.artifactInfo.getArtifactFullPath(),
            userId = context.userId,
            request = request,
            remoteNodes = remoteNodes
        )
        asyncCacheWriter.cache(cacheTask)
    }

    protected fun shouldCache(context: ArtifactContext): Boolean {
        return context.getRemoteConfiguration().cache.enabled && useCache()
    }

    /**
     * 查询[artifactInfo]对应的节点及其父节点
     */
    open fun safeSearchParents(
        repositoryDetail: RepositoryDetail,
        artifactInfo: ArtifactInfo,
    ): List<Any> {
        return emptyList()
    }

    open fun buildCacheWriter(context: ArtifactContext, contentLength: Long): RemoteArtifactCacheWriter {
        val storageCredentials = context.repositoryDetail.storageCredentials
            ?: storageProperties.defaultStorageCredentials()
        val monitor = storageHealthMonitorHelper.getMonitor(storageProperties, storageCredentials)
        val remoteNodes = safeSearchParents(context.repositoryDetail, context.artifactInfo)
        return RemoteArtifactCacheWriter(
            context = context,
            storageManager = storageManager,
            cacheLocks = cacheLocks,
            remoteNodes = remoteNodes,
            metadataService = metadataService,
            monitor = monitor,
            contentLength = contentLength,
            storageProperties = storageProperties
        )
    }

    /**
     * 远程下载响应回调
     */
    open fun onSearchResponse(context: ArtifactSearchContext, response: Response): List<Any> {
        return emptyList()
    }

    /**
     * 远程下载响应回调
     */
    open fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        return null
    }

    /**
     * 获取缓存节点创建请求
     */
    open fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = context.artifactInfo.getArtifactFullPath(),
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            crc64ecma = artifactFile.getFileCrc64ecma(),
            md5 = artifactFile.getFileMd5(),
            overwrite = true,
            operator = context.userId
        )
    }

    /**
     * 生成远程构件下载url
     */
    open fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val configuration = context.getRemoteConfiguration()
        val artifactUri = context.artifactInfo.getArtifactName()
        val queryString = context.request.queryString
        return UrlFormatter.format(configuration.url, artifactUri, queryString)
    }

    /**
     * 创建http client
     */
    protected fun createHttpClient(
        configuration: RemoteConfiguration,
        addInterceptor: Boolean = true,
        followRedirect: Boolean = false,
    ): OkHttpClient {
        return buildOkHttpClient(configuration, addInterceptor, followRedirect).build()
    }

    /**
     * 检查下载响应
     */
    protected fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download artifact from remote failed: [${response.code}]")
            return false
        }
        return true
    }

    /**
     * 检查查询响应
     */
    open fun checkQueryResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Query artifact info from remote failed: [${response.code}]")
            return false
        }
        return true
    }

    /**
     * 创建临时文件并将响应体写入文件
     */
    protected fun createTempFile(body: ResponseBody): ArtifactFile {
        return ArtifactFileFactory.build(body.byteStream())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteRepository::class.java)
    }
}
