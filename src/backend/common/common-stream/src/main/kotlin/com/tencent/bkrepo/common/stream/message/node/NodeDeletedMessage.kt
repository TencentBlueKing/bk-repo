package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType

data class NodeDeletedMessage(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val operator: String
) : IMessage {
    override fun getMessageType() = MessageType.NODE_DELETED
}
