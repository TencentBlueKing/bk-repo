package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TUserRole
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : MongoRepository<TUserRole, String> {
    fun findByUserIdAndProjectId(userId: String, projectId: String): List<TUserRole>
}
