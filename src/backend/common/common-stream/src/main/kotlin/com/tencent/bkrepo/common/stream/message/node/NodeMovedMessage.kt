package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest

data class NodeMovedMessage(val request: NodeMoveRequest) : IMessage {
    override fun getMessageType() = MessageType.NODE_MOVED
}
