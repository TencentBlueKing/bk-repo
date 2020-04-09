package com.tencent.bkrepo.common.stream.message.metadata

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest

data class MetadataSavedMessage(val request: MetadataSaveRequest) : IMessage {
    override fun getMessageType() = MessageType.METADATA_SAVED
}
