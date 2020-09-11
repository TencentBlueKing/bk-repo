package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.constant.METADATA_PREFIX
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import org.springframework.data.mongodb.core.query.Criteria

/**
 * 元数据规则拦截器
 *
 * 条件构造器中传入元数据的条件是`metadata.key=value`，需要适配成mongodb的查询条件
 */
class MetadataRuleInterceptor : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field.startsWith(METADATA_PREFIX)
    }

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        val key = (rule as Rule.QueryRule).field.removePrefix(METADATA_PREFIX)
        val keyRule = Rule.QueryRule(TMetadata::key.name, key, OperationType.EQ).toFixed()
        val valueRule = Rule.QueryRule(TMetadata::value.name, rule.value, rule.operation).toFixed()
        val nestedAndRule = Rule.NestedRule(mutableListOf(keyRule, valueRule))
        val criteria = context.interpreter.resolveRule(nestedAndRule, context)

        return Criteria.where(TNode::metadata.name).elemMatch(criteria)
    }
}
