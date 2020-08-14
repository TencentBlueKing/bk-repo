package com.tencent.bkrepo.common.query.handler.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.handler.MongoQueryRuleHandler
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria
import java.time.LocalDateTime

class BeforeHandler : MongoQueryRuleHandler {

    override fun match(rule: Rule.QueryRule): Boolean {
        return rule.operation == OperationType.BEFORE
    }

    override fun handle(rule: Rule.QueryRule): Criteria {
        return Criteria.where(rule.field).lt(rule.value as LocalDateTime)
    }
}
