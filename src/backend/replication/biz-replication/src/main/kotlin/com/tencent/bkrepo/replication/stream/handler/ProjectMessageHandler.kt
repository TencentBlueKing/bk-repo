package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.message.project.ProjectCreatedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ProjectMessageHandler : AbstractMessageHandler() {
    @Async
    @EventListener(ProjectCreatedMessage::class)
    fun handle(message: ProjectCreatedMessage) {
        with(message.request) {
            getRelativeTaskList(name).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    name = getRemoteProjectId(it, name)
                ).apply { replicationService.replicaProjectCreateRequest(context, this) }
            }
        }
    }
}
