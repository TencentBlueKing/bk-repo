package com.tencent.bkrepo.common.stream.event.node

import com.tencent.bkrepo.common.stream.event.EventType

data class NodeCreatedEvent(
    override val projectId: String,
    override val repoName: String,
    override val repoCategory: String,
    override val fullPath: String,
    val size: Long,
    val sha256: String,
    val md5: String
) : NodeEvent(projectId, repoName, repoCategory, fullPath) {
    override fun getEventType() = EventType.NODE_CREATED
}
