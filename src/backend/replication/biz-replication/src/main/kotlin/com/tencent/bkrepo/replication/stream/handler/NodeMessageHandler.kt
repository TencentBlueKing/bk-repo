package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.request.NodeReplicaRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.replication.service.TaskService
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
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
        getTaskList(message.projectId, message.repoName).forEach {
            val context = ReplicationContext(it)
            // 同步节点
            val replicaRequest = NodeReplicaRequest(
                projectId = getRemoteProjectId(it, message.projectId),
                repoName = getRemoteProjectId(it, message.repoName),
                fullPath = message.fullPath,
                size = message.size,
                sha256 = message.sha256,
                md5 = message.md5
            )
            replicationService.replicaNode(context, replicaRequest)
        }
    }

    @Async
    @EventListener(NodeRenamedMessage::class)
    fun handle(message: NodeRenamedMessage) {
        getTaskList(message.projectId, message.repoName).forEach {
            val context = ReplicationContext(it)
            val replicaRequest = NodeRenameRequest(
                projectId = getRemoteProjectId(it, message.projectId),
                repoName = getRemoteProjectId(it, message.repoName),
                fullPath = message.fullPath,
                newFullPath = message.newFullPath,
                operator = message.operator
            )
            replicationService.replicaNodeRenameRequest(context, replicaRequest)
        }
    }

    @Async
    @EventListener(NodeCopiedMessage::class)
    fun handle(message: NodeCopiedMessage) {
        getTaskList(message.srcProjectId, message.srcRepoName).forEach {
            val context = ReplicationContext(it)
            val replicaRequest = NodeCopyRequest(
                srcProjectId = getRemoteProjectId(it, message.srcProjectId),
                srcRepoName = getRemoteProjectId(it, message.srcRepoName),
                srcFullPath = message.srcFullPath,
                destProjectId = message.destProjectId,
                destRepoName = message.destRepoName,
                destFullPath = message.destFullPath,
                overwrite = message.overwrite,
                operator = message.operator
            )
            replicationService.replicaNodeCopyRequest(context, replicaRequest)
        }
    }

    @Async
    @EventListener(NodeMovedMessage::class)
    fun handle(message: NodeMovedMessage) {
        getTaskList(message.srcProjectId, message.srcRepoName).forEach {
            val context = ReplicationContext(it)
            val replicaRequest = NodeMoveRequest(
                srcProjectId = getRemoteProjectId(it, message.srcProjectId),
                srcRepoName = getRemoteProjectId(it, message.srcRepoName),
                srcFullPath = message.srcFullPath,
                destProjectId = message.destProjectId,
                destRepoName = message.destRepoName,
                destFullPath = message.destFullPath,
                overwrite = message.overwrite,
                operator = message.operator
            )
            replicationService.replicaNodeMovedRequest(context, replicaRequest)
        }
    }

    @Async
    @EventListener(NodeDeletedMessage::class)
    fun handle(message: NodeDeletedMessage) {
        getTaskList(message.projectId, message.repoName).forEach {
            val context = ReplicationContext(it)
            val replicaRequest = NodeDeleteRequest(
                projectId = getRemoteProjectId(it, message.projectId),
                repoName = getRemoteProjectId(it, message.repoName),
                fullPath = message.fullPath,
                operator = message.operator
            )
            replicationService.replicaNodeDeleteRequest(context, replicaRequest)
        }
    }

    private fun getTaskList(projectId: String, repoName: String): List<TReplicationTask> {
        return taskService.listRelativeTask(ReplicationType.INCREMENTAL, projectId, repoName)
    }

    private fun getRemoteProjectId(task: TReplicationTask, sourceProjectId: String): String {
        return task.remoteProjectId ?: task.localProjectId ?: sourceProjectId
    }

    private fun getRemoteRepoName(task: TReplicationTask, sourceRepoName: String): String {
        return task.remoteRepoName ?: task.localRepoName ?: sourceRepoName
    }
}
