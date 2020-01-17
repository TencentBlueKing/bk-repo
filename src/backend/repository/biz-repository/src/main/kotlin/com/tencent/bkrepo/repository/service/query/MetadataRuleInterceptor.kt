package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryBuilder
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
class MetadataRuleInterceptor : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field.startsWith(METADATA_PREFIX)
    }

    override fun intercept(rule: Rule, context: MongoQueryBuilder): Rule {
        val key = (rule as Rule.QueryRule).field.removePrefix(METADATA_PREFIX)
        val keyRule = Rule.QueryRule(METADATA_KEY, key, OperationType.EQ).toFixed()
        val valueRule = Rule.QueryRule(METADATA_VALUE, rule.value, rule.operation).toFixed()
        return Rule.NestedRule(mutableListOf(keyRule, valueRule))
    }

    companion object {
        private const val METADATA_PREFIX = "metadata."
        private const val METADATA_KEY = METADATA_PREFIX + "key"
        private const val METADATA_VALUE = METADATA_PREFIX + "value"
    }
}
