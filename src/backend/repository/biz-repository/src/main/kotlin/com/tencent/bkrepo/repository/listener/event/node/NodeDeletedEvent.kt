package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest

data class NodeDeletedEvent(val request: NodeDeleteRequest) : NodeEvent(request, request.operator) {
    override fun getOperateType() = OperateType.DELETE
}
