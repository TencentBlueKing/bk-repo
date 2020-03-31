package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskRepository : MongoRepository<TReplicationTask, String> {
    fun findAllByTypeAndLocalProjectIdAndLocalRepoName(type: ReplicationType, localProjectId: String?, localRepoName: String?): List<TReplicationTask>
}
