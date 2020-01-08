package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
import java.io.File
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiLocalRepository : LocalRepository() {

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = getNodeCreateRequest(context)
        nodeResource.create(nodeCreateRequest)
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile("content")!!, context.storageCredentials)
    }

    /**
     * 获取PYPI节点创建请求
     */
    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile("content")

        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP]
        map as LinkedHashMap<String, String>
        val sha256 = map["content"]

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            overwrite = true,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile?.getSize(),
            sha256 = sha256,
            operator = context.userId
        )
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

    /**
     * 创建PYPI simple请求
     */
    fun getNodeSearchRequest(context: ArtifactUploadContext): NodeSearchRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile("content")

        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP]
        map as LinkedHashMap<String, String>
        val sha256 = map["content"]

        val repoNameList: MutableList<String> = ArrayList()
        repoNameList.add(repositoryInfo.name)

        val pathVariable: MutableList<String> = ArrayList()
        if (artifactInfo is PypiArtifactInfo) {
            pathVariable.add(artifactInfo.artifactUri)
        }

        val metadataCondition: MutableMap<String, String> = HashMap()

        return NodeSearchRequest(
            projectId = repositoryInfo.projectId,
            repoNameList = repoNameList,
            pathPattern = pathVariable,
            metadataCondition = metadataCondition
        )
    }
}
