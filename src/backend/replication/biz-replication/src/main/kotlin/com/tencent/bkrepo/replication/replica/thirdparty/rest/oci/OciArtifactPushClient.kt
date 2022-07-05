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

package com.tencent.bkrepo.replication.replica.thirdparty.rest.oci

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.util.BasicAuthUtils
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DOCKER_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.thirdparty.RequestProperty
import com.tencent.bkrepo.replication.replica.thirdparty.rest.base.DefaultHandler
import com.tencent.bkrepo.replication.replica.thirdparty.rest.base.PushClient
import com.tencent.bkrepo.replication.util.FileParser
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMethod
import java.io.InputStream
import java.net.URL

/**
 * oci类型制品推送到第三方仓库
 */
@Component
class OciArtifactPushClient(
    private val localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
) : PushClient(localDataManager, replicationProperties) {

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
    override fun processToUploadArtifact(
        nodes: List<NodeDetail>,
        name: String,
        version: String,
        token: String?,
    ): Boolean {
        val manifestInput = loadInputStream(nodes[0])
        val manifestInfo = FileParser.parseManifest(manifestInput)
            ?: throw ArtifactNotFoundException("Can not read manifest info from content")
        var result = false
        logger.info("$name|$version's artifact will be pushed to the third party cluster")
        // 同步layer
        manifestInfo.descriptors?.forEach {
            result = uploadBlobInChunks(
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
            result = buildManifestUploadHandler(
                token = token,
                name = name,
                version = version,
                input = Pair(input, nodes[0].size),
                mediaType = manifestInfo.mediaType
            ).process()
        }
        return result
    }

    /**
     * 获取auth处理器
     */
    override fun getAuthorizationDetails(name: String): String? {
        val ociAuthService = OciAuthorizationService(httpClient)
        return ociAuthService.obtainAuthorizationCode(buildAuthRequestProperties(name))
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
        logger.info("Searching the oci nodes that will be pushed to the third party repository")
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
    private fun buildAuthRequestProperties(name: String): RequestProperty {
        val baseUrl = URL(clusterInfo.url)
        val v2Url = URL(baseUrl, "/v2/").toString()
        val target = baseUrl.path.removePrefix(StringPool.SLASH) + StringPool.SLASH + name
        val scope = "repository:$target:push,pull"
        val authorizationCode = clusterInfo.username?.let {
            BasicAuthUtils.encode(clusterInfo.username!!, clusterInfo.password!!)
        }
        return RequestProperty(
            userName = clusterInfo.username,
            authorizationCode = authorizationCode,
            requestUrl = v2Url,
            requestMethod = RequestMethod.GET,
            scope = scope
        )
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
     * 上传 blob
     */
    private fun uploadBlobInChunks(
        token: String?,
        digest: String,
        name: String,
        version: String,
        projectId: String,
        repoName: String,
    ): Boolean {
        val sessionCloseHandler = buildSessionCloseHandler(
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
        val chunksUploadHandler = buildChunksUploadHandler(
            token = token,
            sessionCloseHandler = sessionCloseHandler,
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
        val sessionIdHandler = buildSessionIdHandler(
            token = token,
            chunksUploadHandler = chunksUploadHandler,
            name = name
        )
        return buildBlobExistCheckHandler(
            token = token,
            sessionIdHandler = sessionIdHandler,
            name = name,
            digest = digest
        ).process()
    }

    /**
     * 根据blob的sha256值判断是否存在，
     * 如不存在则上传
     */
    private fun buildBlobExistCheckHandler(
        token: String?,
        sessionIdHandler: DefaultHandler,
        name: String,
        digest: String,
    ): DefaultHandler {
        val blobExistCheckHandler = DefaultHandler(httpClient)
        val headPath = OCI_BLOB_URL.format(name, digest)
        val headUrl = builderRequestUrl(clusterInfo.url, headPath)
        blobExistCheckHandler.setHandler(failHandler = sessionIdHandler)
        val property = RequestProperty(
            authorizationCode = token,
            requestMethod = RequestMethod.HEAD,
            requestUrl = headUrl
        )
        blobExistCheckHandler.requestProperty = property
        return blobExistCheckHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step1: post获取sessionID
     */
    private fun buildSessionIdHandler(
        token: String?,
        chunksUploadHandler: DefaultHandler,
        name: String,
    ): DefaultHandler {
        val sessionIdHandler = DefaultHandler(httpClient)
        val postPath = OCI_BLOBS_UPLOAD_FIRST_STEP_URL.format(name)
        val postUrl = builderRequestUrl(clusterInfo.url, postPath)
        val postBody: RequestBody = RequestBody.create(
            MediaType.parse("application/json"), StringPool.EMPTY
        )
        sessionIdHandler.setHandler(successHandler = chunksUploadHandler)
        val property = RequestProperty(
            requestBody = postBody,
            authorizationCode = token,
            requestMethod = RequestMethod.POST,
            requestUrl = postUrl
        )
        sessionIdHandler.requestProperty = property
        return sessionIdHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     */
    private fun buildChunksUploadHandler(
        token: String?,
        sessionCloseHandler: DefaultHandler,
        start: Long,
        input: Pair<InputStream, Long>,
    ): DefaultHandler {
        val chunksUploadHandler = DefaultHandler(httpClient)
        val patchBody: RequestBody = RequestBody.create(
            MediaType.parse("application/octet-stream"), input.first.readBytes()
        )
        val patchHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_RANGE, "$start-${start + input.second - 1}")
            .build()
        chunksUploadHandler.setHandler(successHandler = sessionCloseHandler)
        val property = RequestProperty(
            requestBody = patchBody,
            authorizationCode = token,
            requestMethod = RequestMethod.PATCH,
            headers = patchHeader
        )
        chunksUploadHandler.requestProperty = property
        return chunksUploadHandler
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件最后一步: put上传
     */
    private fun buildSessionCloseHandler(token: String?, digest: String): DefaultHandler {
        val sessionCloseHandler = DefaultHandler(httpClient)
        val putBody: RequestBody = RequestBody.create(
            MediaType.parse("application/json"), StringPool.EMPTY
        )
        val putHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .build()
        val params = "digest=$digest"
        val property = RequestProperty(
            requestBody = putBody,
            params = params,
            authorizationCode = token,
            requestMethod = RequestMethod.PUT,
            headers = putHeader
        )
        sessionCloseHandler.requestProperty = property
        return sessionCloseHandler
    }

    /**
     * 生成manifest上传处理方法
     */
    private fun buildManifestUploadHandler(
        token: String?,
        name: String,
        version: String,
        input: Pair<InputStream, Long>,
        mediaType: String?
    ): DefaultHandler {
        logger.info("$name|$version's manifest will be pushed to the remote cluster")
        val manifestUploadHandler = DefaultHandler(httpClient)
        val path = OCI_MANIFEST_URL.format(name, version)
        val putUrl = builderRequestUrl(clusterInfo.url, path)
        val type = mediaType ?: "application/vnd.oci.image.manifest.v1+json"
        val manifestBody: RequestBody = RequestBody.create(
            MediaType.parse(type), input.first.readBytes()
        )
        val manifestHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, type)
            .build()
        val property = RequestProperty(
            requestBody = manifestBody,
            requestUrl = putUrl,
            authorizationCode = token,
            requestMethod = RequestMethod.PUT,
            headers = manifestHeader
        )
        manifestUploadHandler.requestProperty = property
        return manifestUploadHandler
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
        private val logger = LoggerFactory.getLogger(OciArtifactPushClient::class.java)
        const val OCI_LOGIN_URL = "/v2/"
        const val OCI_BLOB_URL = "%s/blobs/%s"
        const val OCI_MANIFEST_URL = "%s/manifests/%s"
        const val OCI_BLOBS_UPLOAD_FIRST_STEP_URL = "%s/blobs/uploads/"

        const val CHUNKED_SIZE = 1024 * 1024 * 50
    }
}
