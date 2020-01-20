package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.stereotype.Component

@Component
class MavenLocalRepository : LocalRepository() {
    /**
     * 获取MAVEN节点创建请求
     */
    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile()
        val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            overwrite = true,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId
        )
    }

}
