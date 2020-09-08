package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TRepository
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 仓库mongo repository
 */
@Repository
interface RepoRepository : MongoRepository<TRepository, String>
