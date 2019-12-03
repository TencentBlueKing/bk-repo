package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.artifact.exception.ArtifactDownloadException
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.FileNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.util.DownloadUtils
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class LocalRepository : AbstractArtifactRepository {

    @Autowired
    lateinit var nodeResource: NodeResource
    @Autowired
    lateinit var fileStorage: FileStorage

    override fun onUpload(context: ArtifactUploadContext) {
        localUpload(context, getNodeCreateRequest(context))
    }

    override fun onDownload(context: ArtifactDownloadContext) {
        localDownload(context, getNodeFullPath(context))
    }

    private fun localUpload(context: ArtifactUploadContext, request: NodeCreateRequest) {
        val repositoryInfo = context.repositoryInfo
        val result = nodeResource.create(request)
        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
            fileStorage.store(request.sha256!!, context.artifactFile.getInputStream(), storageCredentials)
        } else throw ExternalErrorCodeException(result.code, result.message)
    }

    private fun localDownload(context: ArtifactDownloadContext, fullPath: String) {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: throw ArtifactNotFoundException()

        node.nodeInfo.takeIf { !it.folder } ?: throw ArtifactDownloadException("Folder cannot be downloaded.")
        val storageCredentials = CredentialsUtils.readString(repositoryInfo.storageCredentials?.type, repositoryInfo.storageCredentials?.credentials)
        val file = fileStorage.load(node.nodeInfo.sha256!!, storageCredentials) ?: throw FileNotFoundException()

        DownloadUtils.download(node.nodeInfo.name, file, context.request, context.response)
    }
}
