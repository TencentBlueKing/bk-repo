package com.tencent.bkrepo.common.stream.message

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "messageType")
@JsonSubTypes(
    JsonSubTypes.Type(value = NodeCreatedMessage::class, name = MessageType.NODE_CREATED)
)
interface IMessage {
    fun getMessageType(): String
}
