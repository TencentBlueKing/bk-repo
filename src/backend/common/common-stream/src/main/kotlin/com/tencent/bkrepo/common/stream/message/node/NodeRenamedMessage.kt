package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest

data class NodeRenamedMessage(val request: NodeRenameRequest) : IMessage {
    override fun getMessageType() = MessageType.NODE_RENAMED
}
