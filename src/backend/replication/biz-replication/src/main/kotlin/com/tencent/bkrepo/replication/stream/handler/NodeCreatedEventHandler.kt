package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.pojo.request.NodeReplicaRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NodeCreatedEventHandler(
    private val taskService: TaskService,
    private val replicationService: ReplicationService
) {
    @EventListener(NodeCreatedMessage::class)
    fun handle(message: NodeCreatedMessage) {
        val taskList = taskService.listRelativeTask(ReplicationType.INCREMENTAL, message.projectId, message.repoName)
        taskList.forEach {
            val context = ReplicationContext(it)
            // 同步节点
            val replicaRequest = NodeReplicaRequest(
                projectId = it.remoteProjectId ?: it.localProjectId ?: message.projectId,
                repoName = it.remoteRepoName ?: it.localRepoName ?: message.repoName,
                fullPath = message.fullPath,
                size = message.size,
                sha256 = message.sha256,
                md5 = message.md5
            )
            replicationService.replicaNode(context, replicaRequest)
        }
    }
}
