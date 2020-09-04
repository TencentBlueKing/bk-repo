package com.tencent.bkrepo.common.artifact.repository.local

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.core.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.beans.factory.annotation.Autowired

/**
 * 本地仓库抽象逻辑
 */
abstract class LocalRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var storageManager: StorageManager

    override fun onUpload(context: ArtifactUploadContext) {
        with(context) {
            val nodeCreateRequest = buildNodeCreateRequest(this)
            storageManager.storeArtifactFile(nodeCreateRequest, getArtifactFile(), storageCredentials)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val node = nodeClient.detail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            if (node == null || node.folder) return null
            val range = resolveRange(context, node.size)
            val inputStream = storageService.load(node.sha256!!, range, storageCredentials) ?: return null
            return ArtifactResource(inputStream, artifactInfo.getResponseName(), node, ArtifactChannel.LOCAL)
        }
    }

    /**
     * 构造节点创建请求
     */
    open fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = context.artifactInfo.getArtifactFullPath(),
            size = context.getArtifactFile().getSize(),
            sha256 = context.getArtifactSha256(),
            md5 = context.getArtifactMd5(),
            operator = context.userId
        )
    }

    open fun resolveRange(context: ArtifactDownloadContext, total: Long): Range {
        try {
            if (HttpContextHolder.getRequestOrNull()?.method == HttpMethod.HEAD.name) {
                return Range(0, 0, total)
            }
            return HttpRangeUtils.resolveRange(context.request, total)
        } catch (exception: IllegalArgumentException) {
            throw ArtifactException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
        }
    }
}
