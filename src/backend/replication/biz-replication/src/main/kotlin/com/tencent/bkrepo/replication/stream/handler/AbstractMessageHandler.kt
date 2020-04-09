package com.tencent.bkrepo.replication.stream.handler

import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.replication.service.TaskService
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractMessageHandler {

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var replicationService: ReplicationService

    fun getRelativeTaskList(projectId: String, repoName: String? = null): List<TReplicationTask> {
        return taskService.listRelativeTask(ReplicationType.INCREMENTAL, projectId, repoName)
    }

    fun getRemoteProjectId(task: TReplicationTask, sourceProjectId: String): String {
        return task.remoteProjectId ?: task.localProjectId ?: sourceProjectId
    }

    fun getRemoteRepoName(task: TReplicationTask, sourceRepoName: String): String {
        return task.remoteRepoName ?: task.localRepoName ?: sourceRepoName
    }
}
