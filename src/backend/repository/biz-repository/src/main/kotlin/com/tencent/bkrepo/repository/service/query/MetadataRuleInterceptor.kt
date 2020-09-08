package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.api.constant.StringPool.DOT
import com.tencent.bkrepo.common.query.builder.MongoQueryBuilder
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import org.springframework.data.mongodb.core.query.Criteria

class MetadataRuleInterceptor : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field.startsWith(METADATA_PREFIX)
    }

    override fun intercept(rule: Rule, context: MongoQueryBuilder): Criteria {
        val key = (rule as Rule.QueryRule).field.removePrefix(METADATA_PREFIX)
        val keyRule = Rule.QueryRule(TMetadata::key.name, key, OperationType.EQ).toFixed()
        val valueRule = Rule.QueryRule(TMetadata::value.name, rule.value, rule.operation).toFixed()
        val nestedAndRule = Rule.NestedRule(mutableListOf(keyRule, valueRule))
        val criteria = context.resolveRule(nestedAndRule)

        return Criteria.where(TNode::metadata.name).elemMatch(criteria)
    }

    companion object {
        private val METADATA_PREFIX = TNode::metadata.name + DOT
    }
}
