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

package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.PARAM_DOWNLOAD
import com.tencent.bkrepo.common.artifact.constant.PARAM_PREVIEW
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_MD5
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_SHA256
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.remote.buildOkHttpClient
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils.resolveContentRange
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils.resolveRange
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod.HEAD
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.generic.artifact.context.GenericArtifactSearchContext
import com.tencent.bkrepo.generic.artifact.remote.AsyncRemoteArtifactCacheWriter
import com.tencent.bkrepo.generic.artifact.remote.RemoteArtifactCacheLocks
import com.tencent.bkrepo.generic.artifact.remote.RemoteArtifactCacheWriter
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GenericRemoteRepository(
    private val genericProperties: GenericProperties,
    private val storageProperties: StorageProperties,
    private val storageHealthMonitorHelper: StorageHealthMonitorHelper,
    private val asyncCacheWriter: AsyncRemoteArtifactCacheWriter,
    private val cacheLocks: RemoteArtifactCacheLocks,
    private val metadataClient: MetadataClient,
) : RemoteRepository() {
    override fun onDownloadRedirect(context: ArtifactDownloadContext): Boolean {
        return redirectManager.redirect(context)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            val remoteConfiguration = context.getRemoteConfiguration()
            val httpClient = createGenericHttpClient(remoteConfiguration)
            val downloadUrl = createRemoteDownloadUrl(context)
            // 构造request
            val request = Request.Builder().apply {
                if (HttpContextHolder.getRequestOrNull()?.method == HEAD.name) {
                    head()
                } else {
                    get()
                }
                url(downloadUrl)
                // 支持分片下载
                HttpContextHolder.getRequestOrNull()?.getHeader(HttpHeaders.RANGE)
                    ?.let { header(HttpHeaders.RANGE, it) }
            }.build()

            // 发起请求
            val response = httpClient.newCall(request).execute()
            return if (checkResponse(response)) {
                onDownloadResponse(context, response)
            } else null
        }
    }

    override fun loadArtifactResource(cacheNode: NodeDetail, context: ArtifactDownloadContext): ArtifactResource? {
        val range = HttpContextHolder.getRequestOrNull()
            ?.let { resolveRange(it, cacheNode.size) }
            ?: Range.full(cacheNode.size)

        val artifactInputStream = if (shouldReturnEmptyStream(range)) {
            ArtifactInputStream(EmptyInputStream.INSTANCE, range)
        } else {
            storageService.load(cacheNode.sha256!!, range, context.repositoryDetail.storageCredentials)
        }

        return artifactInputStream?.run {
            if (logger.isDebugEnabled) {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit.")
            }
            ArtifactResource(this, context.artifactInfo.getResponseName(), cacheNode, ArtifactChannel.PROXY)
        }
    }

    override fun onDownloadResponse(context: ArtifactDownloadContext, response: okhttp3.Response): ArtifactResource {
        if (response.header(HttpHeaders.TRANSFER_ENCODING) == "chunked") {
            throw ErrorCodeException(PARAMETER_INVALID, "Transfer-Encoding: chunked was not supported")
        }
        val contentLength = response.header(HttpHeaders.CONTENT_LENGTH)!!.toLong()
        val range = resolveContentRange(response.header(HttpHeaders.CONTENT_RANGE)) ?: Range.full(contentLength)

        val request = HttpContextHolder.getRequestOrNull()
        val artifactStream = if (range.isEmpty() || request?.method == HEAD.name) {
            // 返回空文件
            response.close()
            ArtifactInputStream(EmptyInputStream.INSTANCE, range)
        } else {
            // 返回文件内容
            response.body!!.byteStream().artifactStream(range).apply {
                if (range.isFullContent() && context.getRemoteConfiguration().cache.enabled) {
                    // 仅缓存完整文件，返回响应的同时写入缓存
                    addListener(buildCacheWriter(context, contentLength))
                } else if (context.getRemoteConfiguration().cache.enabled) {
                    // 分片下载时异步拉取完整文件并缓存
                    asyncCache(context, response.request)
                }
            }
        }
        // 由于此处node为null，需要手动设置sha256,md5等node相关header
        addHeadersOfNode(response.headers)
        // 设置Content-Disposition响应头
        val preview = context.request.getParameter(PARAM_PREVIEW)?.toBoolean()
        val download = context.request.getParameter(PARAM_DOWNLOAD)?.toBoolean()
        return ArtifactResource(
            inputStream = artifactStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = null,
            channel = ArtifactChannel.LOCAL,
            useDisposition = preview != true || download == true,
        )
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        // 构造url
        val (baseUrl, remoteProjectId, remoteRepoName) = splitBkRepoRemoteUrl(remoteConfiguration.url)
        logger.info("query remoteProject[$remoteProjectId], remoteRepo[$remoteRepoName], user[${context.userId}]")

        val artifactInfo = context.artifactInfo
        val url = UrlFormatter.format(
            baseUrl,
            "/generic/detail/$remoteProjectId/$remoteRepoName/${artifactInfo.getArtifactFullPath()}",
        )

        // 执行请求
        val request = Request.Builder().url(url).get().build()
        return request<Response<NodeDetail>>(context.getRemoteConfiguration(), request).data
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        require(context is GenericArtifactSearchContext)
        return context.queryModel?.let {
            val remoteConfiguration = context.getRemoteConfiguration()

            // 构造url
            val (baseUrl, remoteProjectId, remoteRepoName) = splitBkRepoRemoteUrl(remoteConfiguration.url)
            logger.info("search remoteProject[$remoteProjectId], remoteRepo[$remoteRepoName], user[${context.userId}]")

            val result = searchNodes(context, baseUrl, remoteProjectId, remoteRepoName)
            result.onEach { node ->
                (node as MutableMap<String, Any?>)[RepositoryInfo::category.name] = RepositoryCategory.REMOTE.name
            }
        } ?: emptyList()
    }

    /**
     * 查询[artifactInfo]对应的节点及其父节点
     */
    private fun safeSearchParents(
        repositoryDetail: RepositoryDetail,
        artifactInfo: ArtifactInfo
    ): List<Any> {
        return try {
            val fullPath = artifactInfo.getArtifactFullPath()
            val parents = PathUtils.resolveAncestorFolder(fullPath)
            val rules = mutableListOf<Rule>(
                Rule.QueryRule(NodeDetail::projectId.name, repositoryDetail.projectId),
                Rule.QueryRule(NodeDetail::repoName.name, repositoryDetail.name),
                Rule.QueryRule(NodeDetail::fullPath.name, parents + fullPath, OperationType.IN)
            )
            val queryModel = QueryModel(sort = null, select = null, rule = Rule.NestedRule(rules))
            val searchContext = GenericArtifactSearchContext(
                repo = repositoryDetail,
                artifact = artifactInfo,
                model = queryModel
            )
            search(searchContext)
        } catch (ignored: Exception) {
            logger.warn("Failed to query remote artifact[${artifactInfo}]", ignored)
            emptyList()
        }
    }

    private fun createGenericHttpClient(configuration: RemoteConfiguration): OkHttpClient {
        val platforms = genericProperties.platforms
        val builder = buildOkHttpClient(configuration, false).dns(createPlatformDns(platforms))
        createAuthenticateInterceptor(configuration, platforms)?.let { builder.addInterceptor(it) }
        return builder.build()
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

    private fun buildCacheWriter(context: ArtifactContext, contentLength: Long): RemoteArtifactCacheWriter {
        val storageCredentials = context.repositoryDetail.storageCredentials
            ?: storageProperties.defaultStorageCredentials()
        val monitor = storageHealthMonitorHelper.getMonitor(storageProperties, storageCredentials)
        val remoteNodes = safeSearchParents(context.repositoryDetail, context.artifactInfo)
        return RemoteArtifactCacheWriter(
            context = context,
            storageManager = storageManager,
            cacheLocks = cacheLocks,
            remoteNodes = remoteNodes,
            metadataClient = metadataClient,
            monitor = monitor,
            contentLength = contentLength,
            storageProperties = storageProperties
        )
    }

    private fun searchNodes(
        context: GenericArtifactSearchContext,
        baseUrl: String,
        remoteProjectId: String,
        remoteRepoName: String
    ): List<Any> {
        // 构造url
        val url = UrlFormatter.format(
            baseUrl,
            "generic/$remoteProjectId/$remoteRepoName/search",
        )

        // 构造body
        val newRule = replaceProjectIdAndRepo(context.queryModel!!.rule, remoteProjectId, remoteRepoName)
        val newQueryModel = context.queryModel!!.copy(rule = newRule)
        val body = newQueryModel.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType())

        // 执行请求
        val request = Request.Builder().url(url).post(body).build()
        return request<Response<Page<Map<String, Any?>>>>(context.getRemoteConfiguration(), request).data!!.records
    }

    /**
     * 远程仓库是bkrepo时，okhttp response的header里面包含Node相关header，可以直接写到返回给客户端的响应中
     * 使用此方法可以避免远程Node尚未被缓存时，返回给客户端的响应中不包含Node相关header
     */
    private fun addHeadersOfNode(headers: Headers) {
        val response = HttpContextHolder.getResponse()
        headers[HttpHeaders.ETAG]?.let { response.setHeader(HttpHeaders.ETAG, it) }
        headers[X_CHECKSUM_MD5]?.let { response.setHeader(X_CHECKSUM_MD5, it) }
        headers[X_CHECKSUM_SHA256]?.let { response.setHeader(X_CHECKSUM_SHA256, it) }
    }

    private fun shouldReturnEmptyStream(range: Range? = null): Boolean {
        val rangeToTest = range ?: HttpContextHolder.getRequestOrNull()?.let { resolveRange(it, Long.MAX_VALUE) }
        return HttpContextHolder.getRequestOrNull()?.method == HEAD.name || rangeToTest?.isEmpty() == true
    }

    private inline fun <reified T> request(remoteConfiguration: RemoteConfiguration, request: Request): T {
        val httpClient = createGenericHttpClient(remoteConfiguration)
        val response = httpClient.newCall(request).execute()
        // 解析结果
        return if (response.isSuccessful) {
            response.body!!.byteStream().use { it.readJsonString<T>() }
        } else {
            val msg = response.body?.string()
            logger.warn("request failed, url[${request.url}], code[${response.code}], msg[$msg]")
            throw ErrorCodeException(
                status = HttpStatus.BAD_REQUEST,
                messageCode = GenericMessageCode.ARTIFACT_SEARCH_FAILED,
                params = arrayOf("remote response code[${response.code}]")
            )
        }
    }

    /**
     * 解析出BkRepo Url 的projectId和repoName
     * 比如http://bkrepo.example.com/projectId/repoName将会返回(http://bkrepo.example.com, projectId, repoName)
     */
    private fun splitBkRepoRemoteUrl(url: String): Triple<String, String, String> {
        val httpUrl = url.trimEnd('/').toHttpUrl()
        val builder = httpUrl.newBuilder()
        if (httpUrl.pathSize < 2 || httpUrl.pathSegments[0].isEmpty() || httpUrl.pathSegments[1].isEmpty()) {
            throw ErrorCodeException(
                messageCode = GenericMessageCode.ARTIFACT_SEARCH_FAILED,
                status = HttpStatus.BAD_REQUEST,
                params = arrayOf("failed to split remote url[${url}]")
            )
        }
        for (i in 0 until httpUrl.pathSize) {
            builder.removePathSegment(0)
        }
        return Triple(
            builder.build().toString(),
            httpUrl.pathSegments[httpUrl.pathSize - 2],
            httpUrl.pathSegments[httpUrl.pathSize - 1]
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GenericRemoteRepository::class.java)
    }
}
