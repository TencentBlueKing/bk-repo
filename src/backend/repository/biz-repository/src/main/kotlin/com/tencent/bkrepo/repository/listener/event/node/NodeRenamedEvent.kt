package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest

data class NodeRenamedEvent(val request: NodeRenameRequest) : NodeEvent(request, request.operator) {
    override fun getOperateType() = OperateType.UPDATE
}
