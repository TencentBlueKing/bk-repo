package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TProject
import com.tencent.bkrepo.auth.model.TUser
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : MongoRepository<TProject, String> {
    fun findOneByName(name: String): TProject?
    fun deleteByName(name: String): List<TProject>
}
