/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.external.rest.helm

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.BODY
import com.tencent.bkrepo.replication.constant.PASSWORD
import com.tencent.bkrepo.replication.constant.TOKEN
import com.tencent.bkrepo.replication.constant.URL
import com.tencent.bkrepo.replication.constant.USERNAME
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.external.rest.base.AuthHandler
import com.tencent.bkrepo.replication.replica.external.rest.base.DeployClient
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.replication.util.StreamRequestBody
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.MultipartBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * helm类型制品推送到远端仓库
 */
@Component
class HelmDeployClient(
    private val localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
) : DeployClient(localDataManager, replicationProperties) {

    override fun type(): RepositoryType {
        return RepositoryType.HELM
    }

    /**
     * 上传
     * 推送[name]helm制品
     */
    override fun uploadPackage(
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

    /**
     * 获取auth处理器
     */
    override fun buildAuthHandler(name: String): String? {
        return if (clusterInfo.username.isNullOrBlank()) {
            logger.info("Default auth handler will be used.")
            AuthHandler().obtainToken()
        } else {
            val ociAuthHandler = HelmAuthHandler()
            ociAuthHandler.setRequestProperty(buildAuthRequestProperties(name))
            ociAuthHandler.obtainToken()
        }
    }

    /**
     * 获取需要同步节点列表
     */
    override fun syncNodeList(name: String, version: String, projectId: String, repoName: String): List<NodeDetail> {
        logger.info("Searching the helm nodes that will be deployed to the external repository")
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
        val input = loadInputStream(
            sha256 = node.sha256!!,
            size = node.size,
            projectId = node.projectId,
            repoName = node.repoName
        )
        val fileType = if (node.fullPath.endsWith(CHART_FILE_SUFFIX)) {
            CHART_FILE
        } else PROV_FILE
        return buildHelmPostHandler(
            token = token,
            name = name,
            version = version,
            fileType = fileType,
            input = input,
            size = node.size
        )
    }

    /**
     * 上传chart包或者prov文件
     */
    private fun buildHelmPostHandler(
        token: String?,
        name: String,
        version: String,
        fileType: String,
        input: InputStream,
        size: Long
    ): Boolean {
        val helmPostHandler = HelmPostHandler(deployClient)
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
        // TODO 这里强制同步是否合理，是否增加可配置
        val postBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chart", fileName, StreamRequestBody(input, size))
            .addFormDataPart("force", true.toString())
            .build()
        val postMap = mapOf(BODY to postBody, URL to postUrl, TOKEN to token)
        helmPostHandler.setRequestProperty(postMap)
        return helmPostHandler.process()
    }

    /**
     * 拼接url
     */
    private fun builderRequestUrl(
        url: String,
        path: String,
        params: String = StringPool.EMPTY
    ): String {
        return HttpUtils.builderUrl(url, path, params)
    }

    /**
     * auth鉴权属性配置
     */
    private fun buildAuthRequestProperties(name: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map[USERNAME] = clusterInfo.username
        map[PASSWORD] = clusterInfo.password
        return map
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HelmDeployClient::class.java)
        const val HELM_CHART_PUSH_URL = "/charts"
        const val HELM_PROV_PUSH_URL = "/prov"
        const val PROV_FILE = "prov"
        const val CHART_FILE = "chart"
        const val CHART_FILE_NAME = "/%s-%s.tgz"
        const val PROV_FILE_NAME = "/%s-%s.tgz.prov"
        const val PROV_FILE_SUFFIX = ".prov"
        const val CHART_FILE_SUFFIX = ".tgz"
    }
}
