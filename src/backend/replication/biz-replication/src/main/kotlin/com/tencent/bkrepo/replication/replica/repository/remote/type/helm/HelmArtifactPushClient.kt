/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.replica.repository.remote.type.helm

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.repository.remote.base.PushClient
import com.tencent.bkrepo.replication.util.DefaultHandler
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMethod
import java.io.InputStream
import java.net.URL

/**
 * helm类型制品推送到远端集群
 */
@Component
class HelmArtifactPushClient(
    private val authService: HelmAuthorizationService,
    replicationProperties: ReplicationProperties,
    localDataManager: LocalDataManager
) : PushClient(replicationProperties, localDataManager) {

    override fun type(): RepositoryType {
        return RepositoryType.HELM
    }

    /**
     * 上传[name]helm制品
     */
    override fun processToUploadArtifact(
        nodes: List<NodeDetail>,
        name: String,
        version: String,
        token: String?,
        context: ReplicaContext
    ): Boolean {
        var result = true
        nodes.forEach {
            result = result && uploadChart(
                node = it,
                name = name,
                version = version,
                token = token,
                context = context
            )
            logger.info(
                "The result of uploading chart $name|$version " +
                    "with path ${it.fullPath} to remote cluster ${context.cluster.name} is $result"
            )
        }
        return result
    }

    override fun getAuthorizationDetails(name: String, clusterInfo: ClusterInfo): String? {
        return clusterInfo.username?.let {
            authService.obtainAuthorizationCode(buildAuthRequestProperties(clusterInfo), httpClient)
        }
    }

    /**
     * 获取需要同步节点列表
     */
    override fun querySyncNodeList(
        name: String,
        version: String,
        projectId: String,
        repoName: String
    ): List<NodeDetail> {
        logger.info("Searching the helm nodes that will be deployed to the third party repository")
        // 只拉取manifest文件节点
        val list = mutableListOf<NodeDetail>()
        // 获取chart节点信息
        val chartPath = CHART_FILE_NAME.format(name, version)
        val chartNode = localDataManager.findNodeDetail(projectId, repoName, chartPath)
        list.add(chartNode)
        return list
    }

    /**
     * 读取文件并上传
     */
    private fun uploadChart(
        token: String?,
        name: String,
        node: NodeDetail,
        version: String,
        context: ReplicaContext
    ): Boolean {
        // 先按照非chartmuseum协议进行上传
        var chartUpload = processChartUpload(
            token = token,
            name = name,
            version = version,
            node = node,
            chartMuseum = false,
            context = context
        )
        // 不成功则按照chartmuseum协议进行上传
        if (!chartUpload.isSuccess) {
            chartUpload = processChartUpload(
                token = token,
                name = name,
                version = version,
                node = node,
                chartMuseum = true,
                context = context
            )
        }
        return chartUpload.isSuccess
    }

    /**
     * 上传chart包
     * 针对存在chartMuseum和其他jfrog的上传请求不一致，使用chartMuseum区别
     */
    private fun processChartUpload(
        token: String?,
        name: String,
        version: String,
        chartMuseum: Boolean,
        node: NodeDetail,
        context: ReplicaContext
    ): DefaultHandlerResult {
        val input = localDataManager.loadInputStream(
            sha256 = node.sha256!!,
            size = node.size,
            projectId = node.projectId,
            repoName = node.repoName
        )
        val fileName = CHART_FILE_NAME.format(name, version)
        val requestProperty = if (chartMuseum) {
            buildChartMuseumRequest(
                context = context,
                input = input,
                fileName = fileName,
                token = token,
                size = node.size
            )
        } else {
            buildNonChartMuseumRequest(
                context = context,
                input = input,
                fileName = fileName,
                token = token,
                size = node.size
            )
        }
        return DefaultHandler.process(
            httpClient = httpClient,
            responseType = String::class.java,
            ignoredFailureCode = listOf(HttpStatus.METHOD_NOT_ALLOWED.value, HttpStatus.NOT_FOUND.value),
            requestProperty = requestProperty
        )
    }

    /**
     * 生成非chart museum协议的访问请求数据
     */
    private fun buildNonChartMuseumRequest(
        context: ReplicaContext,
        fileName: String,
        input: InputStream,
        token: String?,
        size: Long
    ): RequestProperty {
        val requestUrl = buildUrl(context.cluster.url, fileName)
        val postBody = RequestBody.create(
            MediaTypes.APPLICATION_OCTET_STREAM.toMediaTypeOrNull(), input.readBytes()
        )
        val requestTag = buildRequestTag(context, fileName, size)
        return RequestProperty(
            requestBody = postBody,
            requestUrl = requestUrl,
            authorizationCode = token,
            requestMethod = RequestMethod.PUT,
            requestTag = requestTag
        )
    }

    /**
     * 生成chart museum协议的访问请求数据
     */
    private fun buildChartMuseumRequest(
        context: ReplicaContext,
        fileName: String,
        input: InputStream,
        token: String?,
        size: Long
    ): RequestProperty {
        val postBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                CHART_FILE, fileName,
                RequestBody.create(
                    MediaTypes.APPLICATION_OCTET_STREAM.toMediaTypeOrNull(), input.readBytes()
                )
            )
            .addFormDataPart("force", "true")
            .build()
        val requestTag = buildRequestTag(context, fileName, size)
        return RequestProperty(
            requestBody = postBody,
            requestUrl = context.cluster.url,
            authorizationCode = token,
            requestMethod = RequestMethod.POST,
            requestTag = requestTag
        )
    }

    /**
     * 拼接url
     */
    private fun buildUrl(
        url: String,
        path: String,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = URL(url)
        val v2Url = URL(baseUrl, baseUrl.path)
        return UrlFormatter.buildUrl(v2Url.toString(), path, params)
    }

    private fun buildAuthRequestProperties(clusterInfo: ClusterInfo): RequestProperty {
        return RequestProperty(
            authorizationCode = BasicAuthUtils.encode(clusterInfo.username!!, clusterInfo.password!!)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HelmArtifactPushClient::class.java)
        const val CHART_FILE = "chart"
        const val CHART_FILE_NAME = "%s-%s.tgz"
    }
}
