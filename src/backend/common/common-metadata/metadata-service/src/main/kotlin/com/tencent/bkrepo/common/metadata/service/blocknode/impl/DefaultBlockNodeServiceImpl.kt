package com.tencent.bkrepo.common.metadata.service.blocknode.impl

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.blocknode.BlockNodeDao
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@Service
@Conditional(SyncCondition::class)
class DefaultBlockNodeServiceImpl(
    blockNodeDao: BlockNodeDao,
    fileReferenceService: FileReferenceService,
    private val nodeClient: NodeClient
) : AbstractBlockNodeService(blockNodeDao, fileReferenceService) {

    override fun getNodeDetail(projectId: String, repoName: String, fullPath: String): NodeDetail {
        return nodeClient.getNodeDetail(projectId, repoName, fullPath).data ?: throw NodeNotFoundException(fullPath)
    }
}
