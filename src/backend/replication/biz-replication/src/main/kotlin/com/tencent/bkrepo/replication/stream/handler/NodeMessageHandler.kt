package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NodeMessageHandler(
    private val taskService: TaskService,
    private val replicationService: ReplicationService
) {
    @Async
    @EventListener(NodeCreatedMessage::class)
    fun handle(message: NodeCreatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeCreateRequest(context, this) }
            }
        }
    }

    @Async
    @EventListener(NodeRenamedMessage::class)
    fun handle(message: NodeRenamedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeRenameRequest(context, this) }
            }
        }
    }

    @Async
    @EventListener(NodeCopiedMessage::class)
    fun handle(message: NodeCopiedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    srcProjectId = getRemoteProjectId(it, projectId),
                    srcRepoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeCopyRequest(context, this) }
            }
        }
    }

    @Async
    @EventListener(NodeMovedMessage::class)
    fun handle(message: NodeMovedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    srcProjectId = getRemoteProjectId(it, projectId),
                    srcRepoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeMoveRequest(context, this) }
            }
        }
    }

    @Async
    @EventListener(NodeDeletedMessage::class)
    fun handle(message: NodeDeletedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeDeleteRequest(context, this) }
            }
        }
    }

    private fun getRelativeTaskList(projectId: String, repoName: String): List<TReplicationTask> {
        return taskService.listRelativeTask(ReplicationType.INCREMENTAL, projectId, repoName)
    }

    private fun getRemoteProjectId(task: TReplicationTask, sourceProjectId: String): String {
        return task.remoteProjectId ?: task.localProjectId ?: sourceProjectId
    }

    private fun getRemoteRepoName(task: TReplicationTask, sourceRepoName: String): String {
        return task.remoteRepoName ?: task.localRepoName ?: sourceRepoName
    }
}
