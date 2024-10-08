package com.tencent.bkrepo.media.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.stereotype.Component

@Component
class MediaLocalRepository : LocalRepository() {
    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo) {
            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, getArtifactFullPath(), context.userId)
            nodeService.deleteNode(nodeDeleteRequest)
        }
    }
}
