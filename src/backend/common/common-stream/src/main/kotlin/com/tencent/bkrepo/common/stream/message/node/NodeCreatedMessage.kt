package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType

data class NodeCreatedMessage(
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val size: Long,
    val sha256: String,
    val md5: String,
    val operator: String
) : IMessage {
    override fun getMessageType() = MessageType.NODE_CREATED
}
