package com.tencent.bkrepo.repository.event.node

import com.tencent.bkrepo.common.artifact.event.AuditableEvent
import com.tencent.bkrepo.common.artifact.event.EventType

/**
 * 节点创建事件
 */
class NodeDeletedEvent(
    override val projectId: String,
    override val repoName: String,
    override val resourceKey: String,
    override val userId: String,
    override val clientAddress: String
) : NodeEvent(), AuditableEvent {

    override val eventType = EventType.DELETED
}
