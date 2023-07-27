package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object RuleUtils {
    /**
     * 检查 rule 中的正则表达式格式
     */
    fun checkRuleRegex(queryRule: Rule.QueryRule) {
        try {
            if (queryRule.operation == OperationType.REGEX)
                Pattern.compile(queryRule.value.toString())
        } catch (e: PatternSyntaxException) {
            throw ErrorCodeException(CommonMessageCode.REGEX_EXPRESSION_PATTERN_ERROR, queryRule.value.toString())
        }
    }

    /**
     * 将 rule 规则中的 path 条件转化为 【前缀匹配正则表达式】
     */
    fun rulePathToRegex(rule: Rule): Rule {
        when (rule) {
            is Rule.NestedRule -> {
                val rules = ArrayList<Rule>(rule.rules.size)
                rule.rules.forEach {
                    rules.add(rulePathToRegex(it))
                }
                return rule.copy(rules = rules)
            }
            is Rule.QueryRule -> {
                if (rule.field == NodeDetail::path.name) {
                    return Rule.QueryRule(rule.field, "^${rule.value}.*", rule.operation)
                }
                return rule
            }
            is Rule.FixedRule -> {
                if (rule.wrapperRule.field == NodeDetail::path.name) {
                    return Rule.QueryRule(
                        rule.wrapperRule.field,
                        "^${rule.wrapperRule.value}.*",
                        rule.wrapperRule.operation
                    )
                }
                return rule
            }
        }
    }
}
