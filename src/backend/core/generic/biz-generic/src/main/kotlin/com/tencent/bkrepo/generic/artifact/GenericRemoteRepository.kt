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

package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.urlEncode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.UrlFormatter
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
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod.HEAD
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.generic.artifact.context.GenericArtifactSearchContext
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.GenericMessageCode
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
                val preview = context.request.getParameter(PARAM_PREVIEW)?.toBoolean()
                val download = context.request.getParameter(PARAM_DOWNLOAD)?.toBoolean()
                onDownloadResponse(context, response, preview != true || download == true, false)
            } else null
        }
    }

    /**
     * 查询[artifactInfo]对应的节点及其父节点
     */
    override fun safeSearchParents(
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

    /**
     * 远程仓库是bkrepo时，okhttp response的header里面包含Node相关header，可以直接写到返回给客户端的响应中
     */
    override fun addHeadersOfNode(headers: Headers) {
        super.addHeadersOfNode(headers)
        val response = HttpContextHolder.getResponse()
        headers[X_CHECKSUM_MD5]?.let { response.setHeader(X_CHECKSUM_MD5, it) }
        headers[X_CHECKSUM_SHA256]?.let { response.setHeader(X_CHECKSUM_SHA256, it) }
    }

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val configuration = context.getRemoteConfiguration()
        val artifactUri = context.artifactInfo.getArtifactFullPath().urlEncode()
        val queryString = context.request.queryString
        return UrlFormatter.format(configuration.url, artifactUri, queryString)
    }

    override fun loadArtifactResource(cacheNode: NodeDetail, context: ArtifactContext): ArtifactResource? {
        return storageManager.loadArtifactInputStream(cacheNode, context.repositoryDetail.storageCredentials)?.run {
            if (logger.isDebugEnabled) {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit.")
            }
            val artifactName = context.artifactInfo.getResponseName()
            val useDisposition = if (context is ArtifactDownloadContext) context.useDisposition else false
            ArtifactResource(this, artifactName, cacheNode, ArtifactChannel.PROXY, useDisposition)
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        // 构造url
        val (baseUrl, remoteProjectId, remoteRepoName) = splitBkRepoRemoteUrl(remoteConfiguration.url)
        logger.info("query remoteProject[$remoteProjectId], remoteRepo[$remoteRepoName], user[${context.userId}]")

        val artifactInfo = context.artifactInfo
        val url = UrlFormatter.format(
            baseUrl,
            "/generic/detail/$remoteProjectId/$remoteRepoName/${artifactInfo.getArtifactFullPath().urlEncode()}",
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

    private fun createGenericHttpClient(configuration: RemoteConfiguration): OkHttpClient {
        val platforms = genericProperties.platforms
        val builder = buildOkHttpClient(configuration, false).dns(createPlatformDns(platforms))
        createAuthenticateInterceptor(configuration, platforms)?.let { builder.addInterceptor(it) }
        return builder.build()
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
