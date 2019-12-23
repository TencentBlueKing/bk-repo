package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.handler.MongoQueryRuleHandler
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.util.MongoEscapeUtils
import org.springframework.data.mongodb.core.query.Criteria

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
class MatchHandler : MongoQueryRuleHandler {

    override fun match(rule: Rule.QueryRule): Boolean {
        return rule.operation == OperationType.PREFIX
    }

    override fun handle(rule: Rule.QueryRule): Criteria {
        val escapedValue = MongoEscapeUtils.escapeRegexExceptWildcard(rule.value.toString())
        val regexPattern = escapedValue.replace("*", ".*")
        return Criteria.where(rule.field).regex(regexPattern)
    }
}
