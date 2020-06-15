package com.tencent.bkrepo.common.artifact.repository.local

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.DownloadStatisticsResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.util.concurrent.Executor
import java.util.regex.Pattern
import javax.annotation.Resource

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class LocalRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var nodeResource: NodeResource

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    @Autowired
    lateinit var downloadStatisticsResource: DownloadStatisticsResource

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = getNodeCreateRequest(context)
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val artifactUri = determineArtifactUri(this)
            val artifactName = determineArtifactName(this)
            val node = nodeResource.detail(repositoryInfo.projectId, repositoryInfo.name, artifactUri).data ?: return null
            node.nodeInfo.takeIf { !it.folder } ?: return null
            val range = resolveRange(context, node.nodeInfo.size)
            val inputStream = storageService.load(node.nodeInfo.sha256!!, range, storageCredentials) ?: return null
            return ArtifactResource(inputStream, artifactName, node.nodeInfo)
        }
    }

    open fun countDownloads(context: ArtifactDownloadContext) {
        taskAsyncExecutor.execute {
            val artifactInfo = context.artifactInfo
            downloadStatisticsResource.add(
                DownloadStatisticsAddRequest(
                    artifactInfo.projectId,
                    artifactInfo.repoName,
                    artifactInfo.artifact,
                    artifactInfo.version
                )
            )
        }
    }

    /**
     * 获取节点fullPath
     */
    open fun determineArtifactUri(context: ArtifactDownloadContext): String {
        return context.artifactInfo.artifactUri
    }

    /**
     * 获取节点创建请求
     */
    open fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile()
        val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId
        )
    }

    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        publisher.publishEvent(ArtifactUploadedEvent(context))
    }

    override fun onDownloadSuccess(context: ArtifactDownloadContext) {
        super.onDownloadSuccess(context)
        countDownloads(context)
    }

    open fun resolveRange(context: ArtifactDownloadContext, total: Long): Range {
        val request = context.request
        val rangeHeader = request.getHeader(HttpHeaders.RANGE)?.trim()
        try {
            if (rangeHeader.isNullOrEmpty()) return Range.ofFull(total)
            val matcher = RANGE_HEADER.matcher(rangeHeader)
            require(matcher.matches()) { "Invalid range header: $rangeHeader" }
            require(matcher.groupCount() >= 1) { "Invalid range header: $rangeHeader" }
            return if (matcher.group(1).isNullOrEmpty()) {
                val start = total - matcher.group(2).toLong()
                val end = total - 1
                Range(start, end, total)
            } else {
                val start = matcher.group(1).toLong()
                val end = if (matcher.group(2).isNullOrEmpty()) total - 1 else matcher.group(2).toLong()
                Range(start, end, total)
            }
        } catch (ex: Exception) {
            logger.warn("Failed to parse range header: $rangeHeader, ex: ${ex.message}")
            throw ArtifactException("Invalid range header", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value())
        }
    }

    companion object {
        private val RANGE_HEADER = Pattern.compile("bytes=(\\d+)?-(\\d+)?")
        private val logger = LoggerFactory.getLogger(LocalRepository::class.java)
    }
}
