package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

interface QueryRuleInterceptor {

    fun match(rule: Rule): Boolean

    fun intercept(rule: Rule, context: MongoQueryInterpreter): Criteria
}
