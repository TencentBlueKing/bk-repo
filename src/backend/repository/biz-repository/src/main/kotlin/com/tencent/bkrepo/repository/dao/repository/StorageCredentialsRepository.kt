package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TStorageCredentials
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StorageCredentialsRepository : MongoRepository<TStorageCredentials, String>
