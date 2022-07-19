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

package com.tencent.bkrepo.replication.replica.base.impl.remote.type.helm

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.util.BasicAuthUtils
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.base.impl.remote.base.DefaultHandler
import com.tencent.bkrepo.replication.replica.base.impl.remote.base.PushClient
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.MediaType
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
    private val localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
) : PushClient(replicationProperties) {

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
    ): Boolean {
        var result = false
        nodes.forEach {
            result = uploadChartOrProv(
                node = it,
                name = name,
                version = version,
                token = token
            )
        }
        return result
    }

    override fun getAuthorizationDetails(name: String): String? {
        return clusterInfo.username?.let {
            val helmAuthorizationService = HelmAuthorizationService()
            helmAuthorizationService.obtainAuthorizationCode(buildAuthRequestProperties())
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
        try {
            // prov 节点不一定存在，不存在则忽略
            val provPath = PROV_FILE_NAME.format(name, version)
            val provNode = localDataManager.findNodeDetail(projectId, repoName, provPath)
            list.add(provNode)
        } catch (ignore: Exception) {
            logger.warn("Prov file does not exist, ignore it")
        }
        return list
    }

    /**
     * 读取文件并上传
     */
    private fun uploadChartOrProv(
        token: String?,
        name: String,
        node: NodeDetail,
        version: String
    ): Boolean {
        val input = localDataManager.loadInputStream(
            sha256 = node.sha256!!,
            size = node.size,
            projectId = node.projectId,
            repoName = node.repoName
        )
        val fileType = if (node.fullPath.endsWith(CHART_FILE_SUFFIX)) {
            CHART_FILE
        } else {
            PROV_FILE
        }
        return buildUploadHandler(
            token = token,
            name = name,
            version = version,
            fileType = fileType,
            input = input
        ).process().isSuccess
    }

    /**
     * 上传chart包或者prov文件
     */
    private fun buildUploadHandler(
        token: String?,
        name: String,
        version: String,
        fileType: String,
        input: InputStream
    ): DefaultHandler {
        val artifactUploadHandler = DefaultHandler(
            httpClient = httpClient,
            responseType = String::class.java
        )
        val postPath: String
        val fileName: String
        if (fileType == CHART_FILE) {
            postPath = HELM_CHART_PUSH_URL
            fileName = CHART_FILE_NAME.format(name, version)
        } else {
            postPath = HELM_PROV_PUSH_URL
            fileName = PROV_FILE_NAME.format(name, version)
        }
        val postUrl = builderRequestUrl(clusterInfo.url, postPath)
        val postBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "chart", fileName,
                RequestBody.create(
                    MediaType.parse("application/octet-stream"), input.readBytes()
                )
            )
            .addFormDataPart("force", true.toString())
            .build()
        val property = RequestProperty(
            requestBody = postBody,
            requestUrl = postUrl,
            authorizationCode = token,
            requestMethod = RequestMethod.POST
        )
        artifactUploadHandler.requestProperty = property
        return artifactUploadHandler
    }

    /**
     * 拼接url
     */
    private fun builderRequestUrl(
        url: String,
        path: String,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = HttpUtils.addProtocol(url)
        val v2Url = URL(baseUrl, "/api" + baseUrl.path)
        return HttpUtils.builderUrl(v2Url.toString(), path, params)
    }

    private fun buildAuthRequestProperties(): RequestProperty {
        return RequestProperty(
            authorizationCode = BasicAuthUtils.encode(clusterInfo.username!!, clusterInfo.password!!)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HelmArtifactPushClient::class.java)
        const val HELM_CHART_PUSH_URL = "charts"
        const val HELM_PROV_PUSH_URL = "prov"
        const val PROV_FILE = "prov"
        const val CHART_FILE = "chart"
        const val CHART_FILE_NAME = "/%s-%s.tgz"
        const val PROV_FILE_NAME = "/%s-%s.tgz.prov"
        const val CHART_FILE_SUFFIX = ".tgz"
    }
}
