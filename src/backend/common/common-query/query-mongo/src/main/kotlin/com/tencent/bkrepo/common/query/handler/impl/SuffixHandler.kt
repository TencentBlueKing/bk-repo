package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.handler.MongoQueryRuleHandler
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.util.MongoEscapeUtils
import org.springframework.data.mongodb.core.query.Criteria

class SuffixHandler : MongoQueryRuleHandler {

    override fun match(rule: Rule.QueryRule): Boolean {
        return rule.operation == OperationType.SUFFIX
    }

    override fun handle(rule: Rule.QueryRule): Criteria {
        val escapedValue = MongoEscapeUtils.escapeRegex(rule.value.toString())
        return Criteria.where(rule.field).regex("$escapedValue$")
    }
}
