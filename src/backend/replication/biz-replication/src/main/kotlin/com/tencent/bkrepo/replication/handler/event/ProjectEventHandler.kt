package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.common.stream.message.project.ProjectCreatedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * handler project message and replicate
 * @author: owenlxu
 * @date: 2020/05/20
 */
@Component
class ProjectEventHandler : AbstractEventHandler() {

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
