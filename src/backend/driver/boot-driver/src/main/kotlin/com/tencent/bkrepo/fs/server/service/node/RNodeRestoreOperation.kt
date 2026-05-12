package com.tencent.bkrepo.fs.server.service.node

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

/**
 * 节点恢复接口
 */
interface RNodeRestoreOperation {

    /**
     * 查询被删除的节点详情
     */
    suspend fun getDeletedNodeDetail(artifact: ArtifactInfo): List<NodeDetail>
}
