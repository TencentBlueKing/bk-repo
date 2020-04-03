package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.message.metadata.MetadataDeletedMessage
import com.tencent.bkrepo.common.stream.message.metadata.MetadataSavedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MetadataMessageHandler : AbstractMessageHandler() {
    @Async
    @EventListener(MetadataSavedMessage::class)
    fun handle(message: MetadataSavedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaMetadataSaveRequest(context, this) }
            }
        }
    }

    @Async
    @EventListener(MetadataDeletedMessage::class)
    fun handle(message: MetadataDeletedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaMetadataDeleteRequest(context, this) }
            }
        }
    }
}
