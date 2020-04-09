package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TOperateLog
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface OperateLogRepository : MongoRepository<TOperateLog, String>
