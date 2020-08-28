package com.tencent.bkrepo.common.artifact.repository.local

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.core.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.DownloadStatisticsClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.Executor
import javax.annotation.Resource

/**
 * 本地仓库抽象逻辑
 */
abstract class LocalRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    @Autowired
    lateinit var downloadStatisticsClient: DownloadStatisticsClient

    @Autowired
    lateinit var storageManager: StorageManager

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    override fun onUpload(context: ArtifactUploadContext) {
        with(context) {
            val nodeCreateRequest = buildNodeCreateRequest(this)
            storageManager.storeArtifactFile(nodeCreateRequest, getArtifactFile(), storageCredentials)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val artifactUri = determineArtifactUri(this)
            val artifactName = determineArtifactName(this)
            val node = nodeClient.detail(projectId, repoName, artifactUri).data ?: return null
            node.takeIf { !it.folder } ?: return null
            val range = resolveRange(context, node.size)
            val inputStream = storageService.load(node.sha256!!, range, storageCredentials) ?: return null
            return ArtifactResource(inputStream, artifactName, node)
        }
    }

    open fun countDownloads(context: ArtifactDownloadContext) {
        taskAsyncExecutor.execute {
            val artifactInfo = context.artifactInfo
            downloadStatisticsClient.add(
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
     * 构造节点创建请求
     */
    open fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = context.artifactInfo.artifactUri,
            size = context.getArtifactFile().getSize(),
            sha256 = context.getArtifactSha256(),
            md5 = context.getArtifactMd5(),
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
        try {
            return HttpRangeUtils.resolveRange(context.request, total)
        } catch (exception: IllegalArgumentException) {
            throw ArtifactException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
        }
    }
}
