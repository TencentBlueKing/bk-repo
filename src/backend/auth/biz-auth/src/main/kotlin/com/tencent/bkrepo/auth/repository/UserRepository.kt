package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.model.TUserRole
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<TUser, String> {
    fun findOneByName(name: String): TUser?
    fun deleteByName(name: String): List<TUser>
}
