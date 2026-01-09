package com.tencent.bkrepo.fs.server.service.node

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convertToDetail
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper.nodeDeletedPointListQuery
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

/**
 * 节点恢复实现
 */
open class RNodeRestoreSupport(
    private val nodeBaseService: RNodeBaseService
) : RNodeRestoreOperation {

    protected val nodeDao = nodeBaseService.nodeDao

    override suspend fun getDeletedNodeDetail(artifact: ArtifactInfo): List<NodeDetail> {
        with(artifact) {
            val query = nodeDeletedPointListQuery(projectId, repoName, getArtifactFullPath())
            val deletedNode = nodeDao.findOne(query)
            return if (deletedNode == null) {
                emptyList()
            } else {
                listOf(convertToDetail(deletedNode)!!)
            }
        }
    }
}
