package com.tencent.bkrepo.common.query.handler

import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

interface MongoNestedRuleHandler {

    fun handle(rule: Rule.NestedRule, context: QueryContext): Criteria
}
