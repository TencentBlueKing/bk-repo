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

package com.tencent.bkrepo.replication.replica.repository.remote.type.oci

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_BLOB_URL
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_URL
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.docker.OciResponse
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.replicator.base.remote.RemoteClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.remote.base.PushClient
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.OciThreadPoolExecutor
import com.tencent.bkrepo.replication.util.DefaultHandler
import com.tencent.bkrepo.replication.util.ManifestParser
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMethod
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor

/**
 * oci类型制品推送到远端仓库
 */
@Component
class OciArtifactPushClient(
    private val authService: OciAuthorizationService,
    replicationProperties: ReplicationProperties,
    localDataManager: LocalDataManager,
    private val artifactReplicationHandler: RemoteClusterArtifactReplicationHandler
) : PushClient(replicationProperties, localDataManager) {

    private val blobUploadExecutor: ThreadPoolExecutor = OciThreadPoolExecutor.instance

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
        context: ReplicaContext
    ): Boolean {
        val manifestInput = localDataManager.loadInputStream(nodes[0])
        val manifestInfo = try {
            ManifestParser.parseManifest(manifestInput)
                ?: throw ArtifactNotFoundException("Can not read manifest info from content")
        } catch (e: Exception) {
            // 针对v1版本的镜像或者manifest.json文件异常时无法获取到对应的节点列表
            throw ArtifactNotFoundException("Could not read manifest.json for remote, $e")
        }
        logger.info("$name|$version's artifact will be pushed to the third party cluster ${context.cluster.name}")
        // 上传layer, 每次并发执行5个
        val semaphore = Semaphore(replicationProperties.threadNum)
        var result = true
        val futureList = mutableListOf<Future<Boolean>>()
        manifestInfo.descriptors?.forEach {
            semaphore.acquire()
            futureList.add(
                submit( Callable {
                    try {
                        retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retries ->
                            logger.info(
                                "Blob $name|$it will be uploaded to the remote cluster " +
                                    "${context.cluster.name.orEmpty()} from repo " +
                                    "${nodes[0].projectId}|${nodes[0].repoName}, try the $retries time"
                            )
                            val checkHandlerResult = processBlobExistCheckHandler(
                                token = token,
                                name = name,
                                digest = it,
                                context = context
                            )
                            if (checkHandlerResult.isSuccess) {
                                logger.info(
                                    "The blob $name|$it is already exist in the " +
                                                "remote cluster ${context.cluster.name}!"
                                )
                                true
                            } else {
                                artifactReplicationHandler.blobPush(
                                    filePushContext = FilePushContext(
                                        token = token,
                                        digest = it,
                                        context = context,
                                        httpClient = httpClient,
                                        responseType = OciResponse::class.java,
                                        name = name
                                    ),
                                    pushType = WayOfPushArtifact.PUSH_WITH_DEFAULT.value
                                )
                            }
                        }
                    } finally {
                        semaphore.release()
                    }
                }.trace()
                )
            )
        }
        try {
            futureList.forEach {
                result = result && it.get()
            }
        } catch (e: ArtifactPushException) {
            // 当出现异常时取消所有任务
            futureList.forEach { it.cancel(true) }
            throw e
        }
        // 同步manifest
        if (result) {
            val targetList = if (context.targetVersions.isNullOrEmpty()) {
                listOf(version)
            } else {
                context.targetVersions!!
            }
            targetList.forEach {
                val input = localDataManager.loadInputStream(nodes[0])
                result = result && processManifestUploadHandler(
                    token = token,
                    name = name,
                    version = it,
                    input = Pair(input, nodes[0].size),
                    mediaType = manifestInfo.mediaType,
                    context = context
                ).isSuccess
            }
        }
        logger.info(
            "The result of uploading $name|$version's artifact " +
                "to remote cluster ${context.cluster.name} is $result"
        )
        return result
    }

    /**
     * 执行一组blob上传任务有返回值的任务
     */
    fun submit(callable: Callable<Boolean>): Future<Boolean> {
        return blobUploadExecutor.submit(callable)
    }

    /**
     * 获取auth处理器
     */
    override fun getAuthorizationDetails(name: String, clusterInfo: ClusterInfo): String? {
        return authService.obtainAuthorizationCode(buildAuthRequestProperties(name, clusterInfo), httpClient)
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
    private fun buildAuthRequestProperties(name: String, clusterInfo: ClusterInfo): RequestProperty {
        val baseUrl = URL(clusterInfo.url)
        val v2Url = URL(baseUrl, "/v2/").toString()
        val target = baseUrl.path.removePrefix(StringPool.SLASH)
            .removeSuffix(StringPool.SLASH) + StringPool.SLASH + name
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
     * 根据blob的sha256值判断是否存在，
     * 如不存在则上传
     */
    private fun processBlobExistCheckHandler(
        token: String?,
        name: String,
        digest: String,
        context: ReplicaContext
    ): DefaultHandlerResult {
        val headPath = OCI_BLOB_URL.format(name, digest)
        val headUrl = artifactReplicationHandler.buildUrl(context.cluster.url, headPath, context)
        val property = RequestProperty(
            authorizationCode = token,
            requestMethod = RequestMethod.HEAD,
            requestUrl = headUrl
        )
        return DefaultHandler.process(
            httpClient = httpClient,
            ignoredFailureCode = listOf(HttpStatus.NOT_FOUND.value),
            extraSuccessCode = listOf(HttpStatus.TEMPORARY_REDIRECT.value),
            responseType = OciResponse::class.java,
            requestProperty = property
        )
    }

    /**
     * 生成manifest上传处理方法
     */
    private fun processManifestUploadHandler(
        token: String?,
        name: String,
        version: String,
        input: Pair<InputStream, Long>,
        mediaType: String?,
        context: ReplicaContext
    ): DefaultHandlerResult {
        logger.info("$name|$version's manifest will be pushed to the remote cluster")
        val path = OCI_MANIFEST_URL.format(name, version)
        val putUrl = artifactReplicationHandler.buildUrl(context.cluster.url, path, context)
        val type = mediaType ?: "application/vnd.oci.image.manifest.v1+json"
        val manifestBody: RequestBody = RequestBody.create(
            type.toMediaTypeOrNull(), input.first.readBytes()
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
        return DefaultHandler.process(
            httpClient = httpClient,
            responseType = OciResponse::class.java,
            requestProperty = property
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciArtifactPushClient::class.java)
    }
}
