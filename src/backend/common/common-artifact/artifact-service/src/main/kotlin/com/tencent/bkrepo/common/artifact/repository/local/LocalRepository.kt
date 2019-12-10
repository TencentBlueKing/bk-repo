package com.tencent.bkrepo.common.artifact.repository.local

import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.File
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class LocalRepository :
    AbstractArtifactRepository {

    @Autowired
    lateinit var nodeResource: NodeResource

    @Autowired
    lateinit var fileStorage: FileStorage

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = getNodeCreateRequest(context)
        val result = nodeResource.create(nodeCreateRequest)
        if (result.isOk()) {
            fileStorage.store(nodeCreateRequest.sha256!!, context.getArtifactFile().getInputStream(), context.storageCredentials)
        } else throw ExternalErrorCodeException(result.code, result.message)
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = getNodeFullPath(context)
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        return fileStorage.load(node.nodeInfo.sha256!!, context.storageCredentials)
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

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile.getSize(),
            sha256 = sha256,
            operator = context.userId
        )
    }
}
