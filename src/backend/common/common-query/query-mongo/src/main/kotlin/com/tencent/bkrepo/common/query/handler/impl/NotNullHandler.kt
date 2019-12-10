package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.handler.MongoQueryRuleHandler
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
class NotNullHandler : MongoQueryRuleHandler {

    override fun match(rule: Rule.QueryRule): Boolean {
        return rule.operation == OperationType.NOT_NULL
    }

    override fun handle(rule: Rule.QueryRule): Criteria {
        return Criteria.where(rule.field).ne(null)
    }
}
