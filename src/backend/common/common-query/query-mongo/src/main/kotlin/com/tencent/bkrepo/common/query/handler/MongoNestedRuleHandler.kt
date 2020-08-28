package com.tencent.bkrepo.common.query.handler

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

interface MongoNestedRuleHandler {

    fun handle(rule: Rule.NestedRule, context: MongoQueryInterpreter): Criteria
}
