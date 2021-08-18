package com.tencent.bkrepo.auth.util.query

import com.tencent.bkrepo.auth.model.TUser
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

object UserQueryHelper {

    fun buildPermissionCheck(userId: String, pwd: String, hashPwd: String): Query {
        val criteria = Criteria()
        criteria.orOperator(
            Criteria.where(TUser::pwd.name).`is`(hashPwd),
            Criteria.where("tokens.id").`is`(pwd)
        ).and(TUser::userId.name).`is`(userId)
        return Query.query(criteria)
    }
}


