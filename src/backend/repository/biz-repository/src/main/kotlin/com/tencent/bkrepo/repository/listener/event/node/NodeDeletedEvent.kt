package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.log.OperateType

data class NodeDeletedEvent (
    override val node: TNode,
    override val operator: String
) : NodeEvent(node, operator) {
    override fun getOperateType() = OperateType.DELETE
}