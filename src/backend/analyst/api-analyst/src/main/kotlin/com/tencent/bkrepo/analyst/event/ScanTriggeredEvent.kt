package com.tencent.bkrepo.analyst.event

import com.tencent.bkrepo.analyst.pojo.SubtaskEventPayload
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

/**
 * 扫描任务触发
 */
class ScanTriggeredEvent(
    val subtask: SubtaskEventPayload
): ArtifactEvent(
    type = EventType.SCAN_TRIGGERED,
    projectId = subtask.projectId,
    repoName = subtask.repoName,
    resourceKey = resourceKey(subtask),
    userId = subtask.createdBy,
) {

    companion object {
        fun resourceKey(subtask: SubtaskEventPayload): String {
            return if (subtask.repoType == RepositoryType.GENERIC.name) {
                subtask.fullPath
            } else {
                "${subtask.packageKey}-${subtask.version}"
            }
        }
    }
}
