package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest

data class NodeMovedEvent(val request: NodeMoveRequest) : NodeEvent(request, request.operator) {
    override fun getOperateType() = OperateType.MOVE
}
