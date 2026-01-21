package com.tencent.bkrepo.analyst.event

import com.tencent.bkrepo.analyst.event.ScanTriggeredEvent.Companion.resourceKey
import com.tencent.bkrepo.webhook.payload.SubtaskEventPayload
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType

class ScanFinishedEvent(
    subtask: SubtaskEventPayload
) : ArtifactEvent(
    type = EventType.SCAN_TRIGGERED,
    projectId = subtask.projectId,
    repoName = subtask.repoName,
    resourceKey = resourceKey(subtask),
    userId = subtask.createdBy,
    data = mapOf("data" to subtask),
)
