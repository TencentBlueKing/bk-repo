package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicationTaskLog
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskLogRepository : MongoRepository<TReplicationTaskLog, String> {
    fun deleteByTaskKey(taskKey: String)
    fun findFirstByTaskKeyOrderByStartTimeDesc(taskKey: String): TReplicationTaskLog?
    fun findByTaskKeyOrderByStartTimeDesc(taskKey: String): List<TReplicationTaskLog>
}
