package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.bson.codecs.pojo.annotations.BsonIgnore

class NodeCreatedEvent constructor(@BsonIgnore val request: NodeCreateRequest) : NodeEvent(request, request.operator) {
    override fun getOperateType() = OperateType.CREATE
}
