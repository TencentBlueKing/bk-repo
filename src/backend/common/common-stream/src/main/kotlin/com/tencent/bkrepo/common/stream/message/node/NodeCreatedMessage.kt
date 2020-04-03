package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest

data class NodeCreatedMessage(val request: NodeCreateRequest) : IMessage {
    override fun getMessageType() = MessageType.NODE_CREATED
}
