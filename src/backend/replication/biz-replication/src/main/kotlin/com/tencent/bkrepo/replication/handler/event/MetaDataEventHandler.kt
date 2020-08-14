package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.common.stream.message.metadata.MetadataDeletedMessage
import com.tencent.bkrepo.common.stream.message.metadata.MetadataSavedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.springframework.context.event.EventListener

/**
 * handler metadata message and replicate
 */
class MetaDataEventHandler : AbstractEventHandler() {

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
