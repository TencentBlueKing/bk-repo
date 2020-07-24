package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.replication.handler.AbstractHandler
import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.service.ReplicationService
import org.springframework.beans.factory.annotation.Autowired

/**
 * AbstractMessageHandler
 * @author: owenlxu
 * @date: 2020/05/20
 */
abstract class AbstractEventHandler : AbstractHandler() {

    @Autowired
    lateinit var replicationService: ReplicationService

    fun getRepoDetail(projectId: String, repoName: String, remoteRepoName: String): ReplicationRepoDetail? {
        val detail = repoDataService.getRepositoryDetail(projectId, repoName) ?: run {
            return null
        }
        return convertReplicationRepo(detail, remoteRepoName)
    }

    fun getRemoteProjectId(task: TReplicationTask, sourceProjectId: String): String {
        return task.remoteProjectId ?: task.localProjectId ?: sourceProjectId
    }

    fun getRemoteRepoName(task: TReplicationTask, sourceRepoName: String): String {
        return task.remoteRepoName ?: task.localRepoName ?: sourceRepoName
    }
}
