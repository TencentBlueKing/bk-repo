package com.tencent.bkrepo.repository.repository

import com.tencent.bkrepo.repository.model.TRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 仓库mongo repository
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Repository
interface RepoRepository : MongoRepository<TRepository, String> {
    fun findByProjectId(projectId: String): List<com.tencent.bkrepo.repository.pojo.Repository>
    fun findByProjectId(projectId: String, pageRequest: PageRequest): Page<com.tencent.bkrepo.repository.pojo.Repository>
}