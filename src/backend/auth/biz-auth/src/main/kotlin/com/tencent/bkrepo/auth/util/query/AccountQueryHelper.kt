package com.tencent.bkrepo.auth.util.query

import com.tencent.bkrepo.auth.model.TAccount
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

object AccountQueryHelper {

    fun checkCredential(accessKey: String, secretKey: String, authorizationGrantType: AuthorizationGrantType?): Query {
        val criteria = Criteria()
        return Query.query(
            criteria.andOperator(
                Criteria.where("credentials.secretKey").`is`(secretKey),
                Criteria.where("credentials.accessKey").`is`(accessKey),
                Criteria().apply {
                    authorizationGrantType?.let {
                        orOperator(
                            Criteria.where("credentials.authorizationGrantType").isEqualTo(null),
                            Criteria.where("credentials.authorizationGrantType").isEqualTo(authorizationGrantType)
                        )
                    }
                }
            )
        )
    }

    fun checkAppAccessKey(appId: String, accessKey: String): Query {
        return Query.query(
            Criteria.where(TAccount::appId.name).`is`(appId)
                .and("credentials.accessKey").`is`(accessKey)
        )
    }
}
