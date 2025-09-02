package com.tencent.bkrepo.common.metadata.pojo.webhook

import com.tencent.bkrepo.common.artifact.event.base.EventType

data class NodeCreatedEventPayload(
    val user: Any,
    val node: Any,
    val eventType: EventType = EventType.NODE_CREATED
)
