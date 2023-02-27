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

package com.tencent.bkrepo.replication.replica.base.impl.remote.type.oci

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.REPOSITORY_INFO
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.docker.OciResponse
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.base.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.base.executor.OciThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.base.impl.remote.base.DefaultHandler
import com.tencent.bkrepo.replication.replica.base.impl.remote.base.PushClient
import com.tencent.bkrepo.replication.replica.base.impl.remote.exception.ArtifactPushException
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.replication.util.ManifestParser
import com.tencent.bkrepo.replication.util.StreamRequestBody
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.ByteString
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
    localDataManager: LocalDataManager
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
        val manifestInfo = ManifestParser.parseManifest(manifestInput)
            ?: throw ArtifactNotFoundException("Can not read manifest info from content")
        logger.info("$name|$version's artifact will be pushed to the third party cluster ${context.cluster.name}")
        // 上传layer, 每次并发执行5个
        val semaphore = Semaphore(replicationProperties.threadNum)
        var result = true
        val futureList = mutableListOf<Future<Boolean>>()
        manifestInfo.descriptors?.forEach {
            semaphore.acquire()
            futureList.add(
                submit {
                    try {
                        retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retries ->
                            logger.info(
                                "Blob $name|$it will be uploaded to the remote cluster " +
                                    "${context.cluster.name.orEmpty()} from repo " +
                                    "${nodes[0].projectId}|${nodes[0].repoName}, try the $retries time"
                            )
                            uploadBlobInChunks(
                                token = token,
                                digest = it,
                                name = name,
                                projectId = nodes[0].projectId,
                                repoName = nodes[0].repoName,
                                context = context
                            )
                        }
                    } finally {
                        semaphore.release()
                    }
                }
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
                    clusterUrl = context.cluster.url
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
     * 读取节点数据流
     */
    private fun getBlobSize(
        sha256: String,
        projectId: String,
        repoName: String,
    ): Long {
        return localDataManager.getNodeBySha256(projectId, repoName, sha256)
    }

    /**
     * 上传 blob
     */
    private fun uploadBlobInChunks(
        token: String?,
        digest: String,
        name: String,
        projectId: String,
        repoName: String,
        context: ReplicaContext
    ): Boolean {
        val clusterUrl = context.cluster.url
        val clusterName = context.cluster.name.orEmpty()
        logger.info(
            "Will try to upload $name's blob $digest " +
                "in repo $projectId|$repoName to remote cluster $clusterName."
        )
        val checkHandlerResult = processBlobExistCheckHandler(
            token = token,
            name = name,
            digest = digest,
            clusterUrl = clusterUrl
        )
        if (checkHandlerResult.isSuccess) {
            logger.info("The blob $name|$digest is already exist in the remote cluster $clusterName!")
            return true
        }
        logger.info("Will try to obtain uuid from remote cluster $clusterName for blob $name|$digest")
        var sessionIdHandlerResult = processSessionIdHandler(
            token = token,
            name = name,
            clusterUrl = clusterUrl
        )
        if (!sessionIdHandlerResult.isSuccess) {
            return false
        }
        val sha256 = digest.split(":").last()
        // 获取对应blob文件大小
        val size = getBlobSize(
            sha256 = sha256,
            projectId = projectId,
            repoName = repoName
        )
        logger.info(
            "Will try to upload blob with ${sessionIdHandlerResult.location} " +
                "in chunked upload way to remote cluster $clusterName for blob $name|$digest"
        )
        // 需要将大文件进行分块上传
        var chunkedUploadResult = try {
            processBlobChunkUpload(
                token = token,
                size = size,
                repoName = repoName,
                projectId = projectId,
                sha256 = sha256,
                location = buildLocationUrl(clusterUrl, sessionIdHandlerResult.location),
                context = context
            )
        } catch (e: Exception) {
            // 针对mirrors不支持将blob分成多块上传，返回404 BLOB_UPLOAD_INVALID
            // 针对csighub不支持将blob分成多块上传，报java.net.SocketException: Broken pipe (Write failed)
            // 针对部分tencentyun.com分块上传报okhttp3.internal.http2.StreamResetException: stream was reset: NO_ERROR
            // 抛出异常后，都进行降级，直接使用单个文件上传进行降级重试
            DefaultHandlerResult(isFailure = true)
        } ?: return false
        if (chunkedUploadResult.isFailure) {
            sessionIdHandlerResult = processSessionIdHandler(
                token = token,
                name = name,
                clusterUrl = clusterUrl
            )
            if (!sessionIdHandlerResult.isSuccess) {
                return false
            }
            chunkedUploadResult = processBlobUploadWithSingleChunk(
                token = token,
                size = size,
                sha256 = sha256,
                projectId = projectId,
                repoName = repoName,
                location = buildLocationUrl(clusterUrl, sessionIdHandlerResult.location),
                context = context
            )
        }

        if (!chunkedUploadResult.isSuccess) return false
        logger.info(
            "The blob $name|$digest is uploaded " +
                "and will try to send a completed request with ${chunkedUploadResult.location}."
        )
        val sessionCloseHandlerResult = processSessionCloseHandler(
            token = token,
            digest = digest,
            location = buildLocationUrl(clusterUrl, chunkedUploadResult.location)
        )
        return sessionCloseHandlerResult.isSuccess
    }

    /**
     * 根据blob的sha256值判断是否存在，
     * 如不存在则上传
     */
    private fun processBlobExistCheckHandler(
        token: String?,
        name: String,
        digest: String,
        clusterUrl: String
    ): DefaultHandlerResult {
        val headPath = OCI_BLOB_URL.format(name, digest)
        val headUrl = buildUrl(clusterUrl, headPath)
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
     * 构件blob上传处理器
     * 上传blob文件step1: post获取sessionID
     */
    private fun processSessionIdHandler(
        token: String?,
        name: String,
        clusterUrl: String
    ): DefaultHandlerResult {
        val postPath = OCI_BLOBS_UPLOAD_FIRST_STEP_URL.format(name)
        val postUrl = buildUrl(clusterUrl, postPath)
        val postBody: RequestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(), StringPool.EMPTY
        )
        val property = RequestProperty(
            requestBody = postBody,
            authorizationCode = token,
            requestMethod = RequestMethod.POST,
            requestUrl = postUrl
        )
        return DefaultHandler.process(
            httpClient = httpClient,
            responseType = OciResponse::class.java,
            requestProperty = property
        )
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     */
    private fun processBlobChunkUpload(
        token: String?,
        size: Long,
        sha256: String,
        projectId: String,
        repoName: String,
        location: String?,
        context: ReplicaContext
    ): DefaultHandlerResult? {
        var startPosition: Long = 0
        var chunkedHandlerResult: DefaultHandlerResult? = null
        while (startPosition < size) {
            val offset = size - startPosition - replicationProperties.chunkedSize
            val byteCount: Long = if (offset < 0) {
                (size - startPosition)
            } else {
                replicationProperties.chunkedSize
            }
            val contentRange = "$startPosition-${startPosition + byteCount - 1}"
            logger.info(
                "${Thread.currentThread().name} start is $startPosition, " +
                    "size is $size, byteCount is $byteCount contentRange is $contentRange"
            )
            val range = Range(startPosition, startPosition + byteCount - 1, size)
            val input = localDataManager.loadInputStreamByRange(sha256, range, projectId, repoName)
            val patchBody: RequestBody = RequestBody.create(
                MediaTypes.APPLICATION_OCTET_STREAM.toMediaTypeOrNull(), input.readBytes()
            )
            val patchHeader = Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_RANGE, contentRange)
                .add(HttpHeaders.CONTENT_LENGTH, "$byteCount")
                .build()
            val requestTag = buildRequestTag(
                context = context,
                key = sha256 + range,
                size = byteCount,
            )
            val property = RequestProperty(
                requestBody = patchBody,
                authorizationCode = token,
                requestMethod = RequestMethod.PATCH,
                headers = patchHeader,
                requestUrl = location,
                requestTag = requestTag
            )
            chunkedHandlerResult = DefaultHandler.process(
                httpClient = httpClient,
                ignoredFailureCode = listOf(HttpStatus.NOT_FOUND.value),
                responseType = OciResponse::class.java,
                requestProperty = property
            )
            if (!chunkedHandlerResult.isSuccess) {
                return chunkedHandlerResult
            }
            startPosition += byteCount
        }
        return chunkedHandlerResult
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     * 针对部分registry不支持将blob分成多块上传，将blob文件整块上传
     */
    private fun processBlobUploadWithSingleChunk(
        token: String?,
        size: Long,
        sha256: String,
        projectId: String,
        repoName: String,
        location: String?,
        context: ReplicaContext
    ): DefaultHandlerResult {
        logger.info("Will upload blob $sha256 in a single patch request")
        val patchBody = StreamRequestBody(localDataManager.loadInputStream(sha256, size, projectId, repoName), size)
        val patchHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_RANGE, "0-${0 + size - 1}")
            .add(REPOSITORY_INFO, "$projectId|$repoName")
            .add(SHA256, sha256)
            .add(HttpHeaders.CONTENT_LENGTH, "$size")
            .build()
        val requestTag = buildRequestTag(context, sha256, size)
        val property = RequestProperty(
            requestBody = patchBody,
            authorizationCode = token,
            requestMethod = RequestMethod.PATCH,
            headers = patchHeader,
            requestUrl = location,
            requestTag = requestTag
        )
        return DefaultHandler.process(
            httpClient = httpClient,
            responseType = OciResponse::class.java,
            requestProperty = property
        )
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件最后一步: put上传
     */
    private fun processSessionCloseHandler(
        token: String?,
        digest: String,
        location: String?
    ): DefaultHandlerResult {
        val putBody: RequestBody = RequestBody.create(
            null, ByteString.EMPTY
        )
        val putHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_LENGTH, "0")
            .build()
        val params = "digest=$digest"
        val property = RequestProperty(
            requestBody = putBody,
            params = params,
            authorizationCode = token,
            requestMethod = RequestMethod.PUT,
            headers = putHeader,
            requestUrl = location
        )
        return DefaultHandler.process(
            httpClient = httpClient,
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
        clusterUrl: String
    ): DefaultHandlerResult {
        logger.info("$name|$version's manifest will be pushed to the remote cluster")
        val path = OCI_MANIFEST_URL.format(name, version)
        val putUrl = buildUrl(clusterUrl, path)
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

    /**
     * 拼接url
     */
    private fun buildUrl(
        url: String,
        path: String,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = URL(url)
        val v2Url = URL(baseUrl, "/v2" + baseUrl.path)
        return HttpUtils.buildUrl(v2Url.toString(), path, params)
    }

    /**
     * 获取上传blob的location
     * 如返回location不带host，需要补充完整
     */
    private fun buildLocationUrl(
        url: String,
        location: String?
    ): String? {
        return location?.let {
            try {
                URL(location)
                location
            } catch (e: Exception) {
                val baseUrl = URL(url)
                val host = URL(baseUrl.protocol, baseUrl.host, StringPool.EMPTY).toString()
                HttpUtils.buildUrl(host, location.removePrefix("/"))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciArtifactPushClient::class.java)
        const val OCI_BLOB_URL = "%s/blobs/%s"
        const val OCI_MANIFEST_URL = "%s/manifests/%s"
        const val OCI_BLOBS_UPLOAD_FIRST_STEP_URL = "%s/blobs/uploads/"
        const val RETRY_COUNT = 2
        const val DELAY_IN_SECONDS: Long = 1
    }
}
