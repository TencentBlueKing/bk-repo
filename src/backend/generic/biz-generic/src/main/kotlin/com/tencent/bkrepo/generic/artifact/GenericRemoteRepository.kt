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

import com.tencent.bkrepo.auth.constant.PIPELINE
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.service.util.okhttp.PlatformAuthInterceptor
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.generic.artifact.context.GenericArtifactSearchContext
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.InetAddress

@Component
class GenericRemoteRepository(
    private val genericProperties: GenericProperties,
) : RemoteRepository() {
    override fun onDownloadRedirect(context: ArtifactDownloadContext): Boolean {
        return redirectManager.redirect(context)
    }

    override fun customHttpClient(builder: OkHttpClient.Builder) {
        builder.dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return genericProperties.platforms.firstOrNull { it.host == hostname && it.ip.isNotEmpty() }?.let {
                    listOf(Inet4Address.getByName(it.ip))
                } ?: Dns.SYSTEM.lookup(hostname)
            }
        })
    }

    override fun createAuthenticateInterceptor(configuration: RemoteConfiguration): Interceptor? {
        val username = configuration.credentials.username
        val password = configuration.credentials.password

        // basic认证
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            return BasicAuthInterceptor(username, password)
        }

        // platform认证
        val url = configuration.url.toHttpUrl()
        genericProperties.platforms.firstOrNull { it.host == url.host || it.ip == url.host }?.let {
            return PlatformAuthInterceptor(it.accessKey, it.secretKey, SecurityUtils.getUserId())
        }

        return null
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val remoteConfiguration = context.getRemoteConfiguration()
        // 构造url
        val (baseUrl, remoteProjectId, remoteRepoName) = splitBkRepoRemoteUrl(remoteConfiguration.url)
        logger.info("query remoteProject[$remoteProjectId], remoteRepo[$remoteRepoName], user[${context.userId}]")

        val artifactInfo = context.artifactInfo
        val url = UrlFormatter.format(
            baseUrl,
            "repository/api/node/detail/$remoteProjectId/$remoteRepoName/${artifactInfo.getArtifactFullPath()}",
            context.request.queryString
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

            val result = if (firstSearch(context) && remoteRepoName == PIPELINE) {
                searchPipelineNodes(context, baseUrl, remoteProjectId)
            } else {
                searchNodes(context, baseUrl, remoteProjectId, remoteRepoName)
            }
            result.onEach { node ->
                (node as MutableMap<String, Any?>)[RepositoryInfo::category.name] = RepositoryCategory.REMOTE.name
            }
        } ?: emptyList()
    }

    /**
     * 请求类似下方例子时，将作为前端首次进入仓库的请求
     *
     * {
     *   ”projectId“: "xxx",
     *   "repoName": "xxx",
     *   "path": "/",
     *   "folder": true // 可选
     * }
     */
    private fun firstSearch(context: GenericArtifactSearchContext): Boolean {
        var result = false
        val rule = context.queryModel?.rule
        if (rule is Rule.NestedRule) {
            for (queryRule in rule.rules) {
                result = false
                if (queryRule !is Rule.QueryRule) {
                    break
                }

                if (queryRule.field !in FIRST_SEARCH_FIELDS || queryRule.operation != OperationType.EQ) {
                    break
                }

                if (queryRule.field == NodeDetail::path.name && queryRule.value != "/") {
                    break
                }
                result = true
            }

        }
        return result
    }

    private fun searchPipelineNodes(
        context: GenericArtifactSearchContext,
        baseUrl: String,
        remoteProjectId: String
    ): List<Any> {
        // 构造url
        val url = UrlFormatter.format(
            baseUrl,
            "repository/api/pipeline/list/$remoteProjectId",
            context.request.queryString
        )
        val request = Request.Builder().get().url(url).build()
        return request<Response<List<Map<String, Any?>>>>(context.getRemoteConfiguration(), request).data!!
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
            "repository/api/node/queryWithoutCount",
            context.request.queryString
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
        val httpClient = createHttpClient(remoteConfiguration)
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
        private val FIRST_SEARCH_FIELDS = listOf(
            NodeDetail::projectId.name,
            NodeDetail::repoName.name,
            NodeDetail::path.name,
            NodeDetail::folder.name
        )
        private val logger = LoggerFactory.getLogger(GenericRemoteRepository::class.java)
    }
}
