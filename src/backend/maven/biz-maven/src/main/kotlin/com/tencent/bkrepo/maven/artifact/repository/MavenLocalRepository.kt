package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.stereotype.Component

@Component
class MavenLocalRepository : LocalRepository() {
    /**
     * 获取MAVEN节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(overwrite = true)
    }
}
