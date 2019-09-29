package com.tencent.bkrepo.repository.repository

import com.tencent.bkrepo.repository.model.TStorageCredentials
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 仓库mongo repository
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Repository
interface StorageCredentialsRepository : MongoRepository<TStorageCredentials, String> {
    fun findByRepositoryId(repositoryId: String): TStorageCredentials?
    fun deleteByRepositoryId(repositoryId: String)
}
