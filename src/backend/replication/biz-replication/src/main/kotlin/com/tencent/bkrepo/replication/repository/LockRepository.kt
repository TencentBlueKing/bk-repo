package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicaLocks
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface LockRepository : MongoRepository<TReplicaLocks, String> {
    fun deleteByTypeAndKeyNameAndKeyGroup(type: String, keyName: String, KeyGroup: String): Long
}
