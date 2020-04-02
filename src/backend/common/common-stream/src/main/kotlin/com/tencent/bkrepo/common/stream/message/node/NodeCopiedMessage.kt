package com.tencent.bkrepo.common.stream.message.node

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType

data class NodeCopiedMessage(
    val srcProjectId: String,
    val srcRepoName: String,
    val srcFullPath: String,
    val destProjectId: String,
    val destRepoName: String,
    val destFullPath: String,
    val overwrite: Boolean,
    val operator: String
) : IMessage {
    override fun getMessageType() = MessageType.NODE_COPIED
}
