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

package com.tencent.bkrepo.replication.replica.external.rest.oci

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.BODY
import com.tencent.bkrepo.replication.constant.DOCKER_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.GET_METHOD
import com.tencent.bkrepo.replication.constant.HEADERS
import com.tencent.bkrepo.replication.constant.METHOD
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.PARAMS
import com.tencent.bkrepo.replication.constant.PASSWORD
import com.tencent.bkrepo.replication.constant.REPOSITORY
import com.tencent.bkrepo.replication.constant.TOKEN
import com.tencent.bkrepo.replication.constant.URL
import com.tencent.bkrepo.replication.constant.USERNAME
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.external.rest.base.AuthHandler
import com.tencent.bkrepo.replication.replica.external.rest.base.DeployClient
import com.tencent.bkrepo.replication.util.FileParser
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URL

/**
 * oci类型制品推送到远端仓库
 */
@Component
class OciDeployClient(
    private val localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
) : DeployClient(localDataManager, replicationProperties) {

    override fun type(): RepositoryType {
        return RepositoryType.OCI
    }

    override fun extraType(): RepositoryType? {
        return RepositoryType.DOCKER
    }

    /**
     * 获取需要上传的文件，解析manifest，上传
     * 推送[name]oci制品
     * 步骤：
     * 1 读取manifest文件中所有digest
     * 2 通过head请求查询对应的digest是否存在：存在，则不用上传；不存在，则上传
     * 3 上传通过post -> patch -> put
     * 4 最后全部上传成功后，再上传manifest
     * 详情：https://github.com/opencontainers/distribution-spec/blob/ef28f81727c3b5e98ab941ae050098ea664c0960/detail.md
     */
    override fun uploadPackage(
        nodes: List<NodeDetail>,
        name: String,
        version: String,
        token: String?,
    ): Boolean {
        val manifestInput = loadInputStream(nodes[0])
        val layerList = FileParser.parseManifest(manifestInput)
        var result = false
        // 同步layer
        layerList.forEach {
            logger.info("$name|$version's blobs will be deployed to the remote cluster")
            result = checkAndUploadBlob(
                token = token,
                digest = it,
                name = name,
                version = version,
                projectId = nodes[0].projectId,
                repoName = nodes[0].repoName
            )
        }
        // 同步manifest
        if (result) {
            val input = loadInputStream(nodes[0])
            result = buildOciManifestPutHandler(
                token = token,
                name = name,
                version = version,
                input = Pair(input, nodes[0].size)
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
            val ociAuthHandler = OciAuthHandler(deployClient)
            ociAuthHandler.setRequestProperty(buildAuthRequestProperties(name))
            ociAuthHandler.obtainToken()
        }
    }

    /**
     * 获取需要同步节点列表
     */
    override fun syncNodeList(name: String, version: String, projectId: String, repoName: String): List<NodeDetail> {
        logger.info("Searching the oci nodes that will be deployed to the external repository")
        // 只拉取manifest文件节点
        var nodePath = OCI_MANIFEST_JSON_FULL_PATH.format(name, version)
        val node = localDataManager.findNode(projectId, repoName, nodePath) ?: run {
            // manifest文件地址需要考虑兼容
            nodePath = DOCKER_MANIFEST_JSON_FULL_PATH.format(name, version)
            localDataManager.findNodeDetail(projectId, repoName, nodePath)
        }
        return mutableListOf(node)
    }

    /**
     * auth鉴权属性配置
     */
    private fun buildAuthRequestProperties(name: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map[USERNAME] = clusterInfo.username
        map[PASSWORD] = clusterInfo.password
        val baseUrl = URL(clusterInfo.url)
        val v2Url = URL(baseUrl, "/v2")
        val authUrl = UrlFormatter.format(v2Url.toString())
        map[URL] = authUrl
        map[METHOD] = GET_METHOD
        map[REPOSITORY] = baseUrl.path.removePrefix(StringPool.SLASH) + StringPool.SLASH + name
        return map
    }

    /**
     * 读取节点数据流
     */
    private fun loadInputStream(
        sha256: String,
        name: String,
        version: String,
        projectId: String,
        repoName: String,
    ): Pair<InputStream, Long> {
        var nodePath = OCI_LAYER_FULL_PATH.format(name, "sha256__$sha256")
        val nodeInfo = localDataManager.findNode(projectId, repoName, nodePath) ?: run {
            // 需要考虑历史数据兼容问题,旧的docker仓库存储路径不一致
            nodePath = DOCKER_LAYER_FULL_PATH.format(name, version, "sha256__$sha256")
            localDataManager.findNodeDetail(projectId, repoName, nodePath)
        }
        return Pair(
            loadInputStream(
                sha256 = sha256,
                size = nodeInfo.size,
                projectId = projectId,
                repoName = repoName
            ),
            nodeInfo.size
        )
    }

    /**
     * 检查blob是否存在，如不存在则上传；存在则返回true
     */
    private fun checkAndUploadBlob(
        token: String?,
        digest: String,
        name: String,
        version: String,
        projectId: String,
        repoName: String,
    ): Boolean {
        val ociPutHandler = buildOciPutHandler(
            token = token,
            digest = digest
        )
        // 加载blob流数据
        val sha256 = digest.split(":").last()
        val (inputStream, size) = loadInputStream(
            sha256 = sha256,
            name = name,
            version = version,
            projectId = projectId,
            repoName = repoName
        )
        // 需要将大文件进行分块上传
        val ociPatchHandler = buildOciPatchHandler(
            token = token,
            ociPutHandler = ociPutHandler,
            start = 0,
            input = Pair(inputStream, size)
        )

//            val ociPatchHandler = builderChunkedPatchHandler(
//                token = token,
//                ociPutHandler = ociPutHandler,
//                start = 0,
//                input = inputStream,
//                size = size
//            )
        val ociPostHandler = buildOciPostHandler(
            token = token,
            ociPatchHandler = ociPatchHandler,
            name = name
        )
        return buildOciHeadHandler(
            token = token,
            ociPostHandler = ociPostHandler,
            name = name,
            digest = digest
        )
    }

    /**
     * 根据blob的sha256值判断是否存在，
     * 如不存在则上传
     */
    private fun buildOciHeadHandler(
        token: String?,
        ociPostHandler: OciPostHandler,
        name: String,
        digest: String,
    ): Boolean {
        val ociHeadHandler = OciHeadHandler(deployClient)
        val headPath = OCI_BLOB_HEAD_URL.format(name, digest)
        val headUrl = builderRequestUrl(clusterInfo.url, headPath)
        val headMap = mapOf(URL to headUrl, TOKEN to token)
        ociHeadHandler.setRequestProperty(headMap)
        ociHeadHandler.setHandler(failHandler = ociPostHandler)
        return ociHeadHandler.process()
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step1: post获取uuid
     */
    private fun buildOciPostHandler(
        token: String?,
        ociPatchHandler: OciPatchHandler,
        name: String,
    ): OciPostHandler {
        val ociPostHandler = OciPostHandler(deployClient)
        val postPath = OCI_BOLBS_UPLOAD_FIRST_STEP_URL.format(name)
        val postUrl = builderRequestUrl(clusterInfo.url, postPath)
        val postBody: RequestBody = RequestBody.create(
            MediaType.parse("application/json"), StringPool.EMPTY
        )
        val postMap = mapOf(BODY to postBody, URL to postUrl, TOKEN to token)
        ociPostHandler.setRequestProperty(postMap)
        ociPostHandler.setHandler(successHandler = ociPatchHandler)
        return ociPostHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     */
    private fun buildOciPatchHandler(
        token: String?,
        ociPutHandler: OciPutHandler,
        start: Long,
        input: Pair<InputStream, Long>,
    ): OciPatchHandler {
        val ociPatchHandler = OciPatchHandler(deployClient)
        val patchBody: RequestBody = RequestBody.create(
            MediaType.parse("application/octet-stream"), input.first.readBytes()
        )
        val patchHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_RANGE, "$start-${start + input.second - 1}")
            .build()
        val patchMap = mapOf(BODY to patchBody, HEADERS to patchHeader, TOKEN to token)
        ociPatchHandler.setRequestProperty(patchMap)
        ociPatchHandler.setHandler(successHandler = ociPutHandler)
        return ociPatchHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     */
    private fun builderChunkedPatchHandler(
        token: String?,
        input: InputStream,
        start: Int,
        size: Long,
        ociPutHandler: OciPutHandler
    ): OciPatchHandler {
        // TODO 分块上传待调试
        val offset = size - start - CHUNKED_SIZE
        val byteCount = if (offset < 0) {
            (size - start).toInt()
        } else {
            CHUNKED_SIZE
        }
        val ociPatchHandler = OciPatchHandler(deployClient)
        val patchBody: RequestBody = RequestBody.create(
            MediaType.parse("application/octet-stream"), input.readBytes(), start, byteCount
        )
        val patchHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_RANGE, "$start-${start + byteCount - 1}")
            .build()
        val patchMap = mapOf(BODY to patchBody, HEADERS to patchHeader, TOKEN to token)
        ociPatchHandler.setRequestProperty(patchMap)
        if (byteCount < CHUNKED_SIZE) {
            ociPatchHandler.setHandler(successHandler = ociPutHandler)
        } else {
            ociPatchHandler.setHandler(
                successHandler = builderChunkedPatchHandler(
                    token = token,
                    input = input,
                    start = start + CHUNKED_SIZE,
                    size = size,
                    ociPutHandler = ociPutHandler
                )
            )
        }
        return ociPatchHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件最后一步: put上传
     */
    private fun buildOciPutHandler(token: String?, digest: String): OciPutHandler {
        val ociPutHandler = OciPutHandler(deployClient)
        val putBody: RequestBody = RequestBody.create(
            MediaType.parse("application/json"), StringPool.EMPTY
        )
        val putHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .build()
        val params = "digest=$digest"
        val putMap = mapOf(BODY to putBody, HEADERS to putHeader, TOKEN to token, PARAMS to params)
        ociPutHandler.setRequestProperty(putMap)
        return ociPutHandler
    }

    /**
     * 上传manifest
     */
    private fun buildOciManifestPutHandler(
        token: String?,
        name: String,
        version: String,
        input: Pair<InputStream, Long>,
    ): Boolean {
        logger.info("$name|$version's manifest will be deployed to the remote cluster")
        val ociPutHandler = OciPutHandler(deployClient)
        val path = OCI_MANIFEST_URL.format(name, version)
        val putUrl = builderRequestUrl(clusterInfo.url, path)
        val manifestBody: RequestBody = RequestBody.create(
            MediaType.parse("application/octet-stream"), input.first.readBytes()
        )
        val manifestHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, "application/vnd.oci.image.manifest.v1+json")
            .build()
        val putMap = mapOf(URL to putUrl, BODY to manifestBody, HEADERS to manifestHeader, TOKEN to token)
        ociPutHandler.setRequestProperty(putMap)
        return ociPutHandler.process()
    }

    /**
     * 拼接url
     */
    private fun builderRequestUrl(
        url: String,
        path: String,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = URL(url)
        val v2Url = URL(baseUrl, "/v2" + baseUrl.path)
        return HttpUtils.builderUrl(v2Url.toString(), path, params)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciDeployClient::class.java)
        const val OCI_LOGIN_URL = "/v2/"
        const val OCI_BLOB_HEAD_URL = "%s/blobs/%s"
        const val OCI_MANIFEST_URL = "%s/manifests/%s"
        const val OCI_BOLBS_UPLOAD_FIRST_STEP_URL = "%s/blobs/uploads/"
        const val CHUNKED_SIZE = 1024 * 1024 * 50
    }
}
