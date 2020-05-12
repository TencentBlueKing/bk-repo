package com.tencent.bkrepo.npm.dao.repository

import com.tencent.bkrepo.npm.model.TMigrationErrorData
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MigrationErrorDataRepository : MongoRepository<TMigrationErrorData, String>
