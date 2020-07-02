package com.tencent.bkrepo.replication.handler

import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.service.RepoDataService
import com.tencent.bkrepo.replication.service.TaskService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractHandler {

    @Autowired
    lateinit var repoDataService: RepoDataService

    @Autowired
    lateinit var taskService: TaskService

    fun convertReplicationRepo(localRepoInfo: RepositoryInfo, remoteRepoName: String? = null): ReplicationRepoDetail {
        return with(localRepoInfo) {
            val fileCount = repoDataService.countFileNode(this)
            ReplicationRepoDetail(
                localRepoInfo = this,
                fileCount = fileCount,
                remoteRepoName = remoteRepoName ?: this.name
            )
        }
    }

    fun getRelativeTaskList(projectId: String, repoName: String? = null): List<TReplicationTask> {
        return taskService.listRelativeTask(ReplicationType.INCREMENTAL, projectId, repoName)
    }
}
