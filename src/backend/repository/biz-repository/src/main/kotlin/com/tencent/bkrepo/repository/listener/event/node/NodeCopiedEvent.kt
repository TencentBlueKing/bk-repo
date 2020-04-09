package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest

data class NodeCopiedEvent(val request: NodeCopyRequest) : NodeEvent(request, request.operator) {
    override fun getOperateType() = OperateType.UPDATE
}
