package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.common.stream.message.repo.RepoCreatedMessage
import com.tencent.bkrepo.common.stream.message.repo.RepoDeletedMessage
import com.tencent.bkrepo.common.stream.message.repo.RepoUpdatedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * handler repository message and replicate
 * @author: owenlxu
 * @date: 2020/05/20
 */
@Component
class RepoEventHandler : AbstractEventHandler() {

    @EventListener(RepoCreatedMessage::class)
    fun handle(message: RepoCreatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, name).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    name = getRemoteRepoName(it, name)
                ).apply { replicationService.replicaRepoCreateRequest(context, this) }
            }
        }
    }

    @EventListener(RepoUpdatedMessage::class)
    fun handle(message: RepoUpdatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, name).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    name = getRemoteRepoName(it, name)
                ).apply { replicationService.replicaRepoUpdateRequest(context, this) }
            }
        }
    }

    @EventListener(RepoDeletedMessage::class)
    fun handle(message: RepoDeletedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, name).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    name = getRemoteRepoName(it, name)
                ).apply { replicationService.replicaRepoDeleteRequest(context, this) }
            }
        }
    }
}
