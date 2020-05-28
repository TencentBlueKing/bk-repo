package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.pypi.artifact.model.TMigrateData
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MigrateDataRepository : MongoRepository<TMigrateData, String>
