package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.query.handler.MongoNestedRuleHandler
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

class DefaultMongoNestedRuleHandler : MongoNestedRuleHandler {

    override fun handle(rule: Rule.NestedRule, context: QueryContext): Criteria {
        val criteriaArray = rule.rules.map { context.interpreter.resolveRule(it, context) }.toTypedArray()
        return when (rule.relation) {
            Rule.NestedRule.RelationType.AND -> Criteria().andOperator(*criteriaArray)
            Rule.NestedRule.RelationType.OR -> Criteria().orOperator(*criteriaArray)
            Rule.NestedRule.RelationType.NOR -> Criteria().norOperator(*criteriaArray)
        }
    }
}
