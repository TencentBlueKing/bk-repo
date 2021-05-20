package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicationTaskLogDetail
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskLogDetailRepository : MongoRepository<TReplicationTaskLogDetail, String> {
    fun deleteByTaskLogKey(taskLogKey: String)
    fun findByTaskLogKey(taskLogKey: String): List<TReplicationTaskLogDetail>
}
