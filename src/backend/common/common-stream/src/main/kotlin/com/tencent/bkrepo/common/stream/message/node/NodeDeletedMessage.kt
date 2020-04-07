package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest

data class NodeDeletedMessage(val request: NodeDeleteRequest) : IMessage {
    override fun getMessageType() = MessageType.NODE_DELETED
}
