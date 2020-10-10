package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.exception.HelmFileAlreadyExistsException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmLocalRepository : LocalRepository() {

    // override fun determineArtifactName(context: ArtifactContext): String {
    //     val fileName = context.artifactInfo.getArtifactFullPath().trimStart('/')
    //     return if (StringUtils.isBlank(fileName)) INDEX_YAML else fileName
    // }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        // 判断是否是强制上传
        val isForce = context.request.getParameter("force")?.let { true } ?: false
        context.putAttribute("force", isForce)
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        context.getArtifactFileMap().entries.forEach { (name, _) ->
            val fullPath = context.getStringAttribute(name + "_full_path")!!
            val isExist = nodeClient.exist(projectId, repoName, fullPath).data!!
            if (isExist && !isOverwrite(fullPath, isForce)) {
                throw HelmFileAlreadyExistsException("${fullPath.trimStart('/')} already exists")
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.getArtifactFileMap().entries.forEach { (name, _) ->
            val nodeCreateRequest = getNodeCreateRequest(name, context)
            storageManager.storeArtifactFile(nodeCreateRequest, context.getArtifactFile(name), context.storageCredentials)
        }
    }

    private fun getNodeCreateRequest(name: String, context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val artifactFile = context.getArtifactFile(name)
        val sha256 = context.getArtifactSha256(name)
        val md5 = context.getArtifactMd5(name)
        val fullPath = context.getStringAttribute(name + FULL_PATH)!!
        val isForce = context.getBooleanAttribute("force")!!
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            metadata = parseMetaData(fullPath, isForce),
            overwrite = isOverwrite(fullPath, isForce)
        )
    }

    private fun parseMetaData(fullPath: String, isForce: Boolean): Map<String, String>? {
        if (isOverwrite(fullPath, isForce) || !fullPath.endsWith(".tgz")) {
            return emptyMap()
        }
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf('.') - 1)
        val name = substring.substringBeforeLast('-')
        val version = substring.substringAfterLast('-')
        return mapOf("name" to name, "version" to version)
    }

    private fun isOverwrite(fullPath: String, isForce: Boolean): Boolean {
        return isForce || !(fullPath.trim().endsWith(".tgz", true) || fullPath.trim().endsWith(".prov", true))
    }

    // override fun determineArtifactUri(context: ArtifactDownloadContext): String {
    //     return context.getStringAttribute(FULL_PATH)!!
    // }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        with(context) {
            val node =  nodeClient.detail(projectId, repoName, fullPath).data
            if (node == null || node.folder) return null
            val range = resolveRange(context, node.size)
            val inputStream = storageService.load(node.sha256!!, range, storageCredentials) ?: return null
            return ArtifactResource(inputStream, artifactInfo.getResponseName(), node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    override fun query(context: ArtifactQueryContext): ArtifactInputStream? {
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        return this.onQuery(context) ?: throw HelmFileNotFoundException("Artifact[$fullPath] does not exist")
    }

    private fun onQuery(context: ArtifactQueryContext): ArtifactInputStream? {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        val node = nodeClient.detail(projectId, repoName, fullPath).data
        if (node == null || node.folder) return null
        return storageService.load(
            node.sha256!!, Range.full(node.size), context.storageCredentials
        )?.also { logger.info("search artifact [$fullPath] success!") }
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        val userId = context.userId
        val isExist = nodeClient.exist(projectId, repoName, fullPath).data!!
        if (!isExist) {
            throw HelmFileNotFoundException("remove $fullPath failed: no such file or directory")
        }
        nodeClient.delete(NodeDeleteRequest(projectId, repoName, fullPath, userId))
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
