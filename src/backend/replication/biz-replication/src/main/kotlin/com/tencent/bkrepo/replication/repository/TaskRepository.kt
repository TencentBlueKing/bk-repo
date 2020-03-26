package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicationTask
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskRepository : MongoRepository<TReplicationTask, String>
