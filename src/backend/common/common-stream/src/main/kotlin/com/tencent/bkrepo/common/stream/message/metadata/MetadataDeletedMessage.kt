package com.tencent.bkrepo.common.stream.message.metadata

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.message.MessageType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest

data class MetadataDeletedMessage(val request: MetadataDeleteRequest) : IMessage {
    override fun getMessageType() = MessageType.METADATA_DELETED
}
