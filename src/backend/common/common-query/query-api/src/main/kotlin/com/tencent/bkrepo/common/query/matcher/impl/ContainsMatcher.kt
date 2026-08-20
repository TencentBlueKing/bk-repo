package com.tencent.bkrepo.common.query.matcher.impl

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule

class ContainsMatcher : RuleMatcher() {
    override fun supportOperationType() = OperationType.CONTAINS

    override fun doMatch(rule: Rule.QueryRule, valueToMatch: Any?): Boolean {
        require(rule.value is String && valueToMatch is String)
        return valueToMatch.contains(rule.value)
    }
}
