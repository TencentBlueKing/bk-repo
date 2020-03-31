package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.common.stream.event.node.NodeCreatedEvent
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
    @EventListener(NodeCreatedEvent::class)
    fun handle(event: NodeCreatedEvent) {
        val taskList = taskService.listRelativeTask(ReplicationType.INCREMENTAL, event.projectId, event.repoName)
        taskList.forEach {
            val context = ReplicationContext(it)
            // 同步节点
            val replicaRequest = NodeReplicaRequest(
                projectId = it.remoteProjectId ?: it.localProjectId ?: event.projectId,
                repoName = it.remoteRepoName ?: it.localRepoName ?: event.repoName,
                fullPath = event.fullPath,
                size = event.size,
                sha256 = event.sha256,
                md5 = event.md5
            )
            replicationService.replicaNode(context, replicaRequest)
        }
    }
}
