package com.tencent.bkrepo.common.stream.event

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.stream.event.node.NodeCreatedEvent

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(value = NodeCreatedEvent::class, name = EventType.Constants.NODE_CREATED)
)
interface IEvent {
    fun getEventType(): EventType
}
