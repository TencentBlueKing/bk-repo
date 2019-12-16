package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TUser
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<TUser, String> {
    fun findOneByUId(uid: String): TUser?
    fun deleteByUId(uid: String)
}
