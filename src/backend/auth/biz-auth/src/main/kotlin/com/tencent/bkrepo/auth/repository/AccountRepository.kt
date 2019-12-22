package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TAccount
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository


@Repository
interface AccountRepository : MongoRepository<TAccount, String> {
    fun findOneByAppId(appId: String): TAccount?
    fun deleteByAppId(uid: String):Long
    fun findAllBy():List<TAccount>
}