package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.log.OperateType

data class NodeMovedEvent(
    override val node: TNode,
    override val operator: String,
    val destProjectId: String,
    val destRepoName: String,
    val destFullPath: String,
    val overwrite: Boolean
) : NodeEvent(node, operator) {
    override fun getOperateType() = OperateType.UPDATE
}
