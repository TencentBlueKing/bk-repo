package com.tencent.bkrepo.common.artifact.repository.local

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.ArtifactDownloadCountResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.File
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher

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
    lateinit var artifactDownloadCountResource: ArtifactDownloadCountResource

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = getNodeCreateRequest(context)
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = getNodeFullPath(context)
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        return storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
    }

    override fun onDownloadSuccess(context: ArtifactDownloadContext, file: File) {
        super.onDownloadSuccess(context, file)
        countDownloads(context)
    }

    open fun countDownloads(context: ArtifactDownloadContext){
        val artifactInfo = context.artifactInfo
        artifactDownloadCountResource.create(
            DownloadCountCreateRequest(
                artifactInfo.projectId,
                artifactInfo.repoName,
                artifactInfo.artifact,
                artifactInfo.version
            )
        )
    }

    /**
     * 获取节点fullPath
     */
    open fun getNodeFullPath(context: ArtifactDownloadContext): String {
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
}
