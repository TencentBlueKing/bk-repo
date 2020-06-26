package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TOperateLog
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface OperateLogRepository : MongoRepository<TOperateLog, String>
