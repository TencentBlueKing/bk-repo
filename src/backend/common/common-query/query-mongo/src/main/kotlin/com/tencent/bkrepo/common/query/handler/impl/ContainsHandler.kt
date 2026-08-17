package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.handler.MongoQueryRuleHandler
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

class ContainsHandler : MongoQueryRuleHandler {

    override fun match(rule: Rule.QueryRule): Boolean {
        return rule.operation == OperationType.CONTAINS
    }

    override fun handle(rule: Rule.QueryRule): Criteria {
        val escapedValue = EscapeUtils.escapeRegex(rule.value.toString())
        return Criteria.where(rule.field).regex(escapedValue)
    }
}
