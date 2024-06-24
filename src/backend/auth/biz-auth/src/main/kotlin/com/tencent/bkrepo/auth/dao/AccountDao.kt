package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TAccount
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository


@Repository
class AccountDao : SimpleMongoDao<TAccount>() {

    fun findOneByAppId(appId: String): TAccount? {
        val query = Query(Criteria(TAccount::appId.name).`is`(appId))
        return this.findOne(query)
    }

    fun findAllBy(): List<TAccount> {
        return this.findAll()
    }


    fun findByOwner(owner: String): List<TAccount> {
        val query = Query(Criteria(TAccount::owner.name).`is`(owner))
        return this.find(query)
    }

    fun findByIdIn(ids: List<String>): List<TAccount> {
        val query = Query(Criteria(TAccount::id.name).`in`(ids))
        return this.find(query)
    }

}