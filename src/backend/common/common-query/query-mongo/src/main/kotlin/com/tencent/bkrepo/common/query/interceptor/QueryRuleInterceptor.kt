package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

/**
 * 查询规则拦截器
 */
interface QueryRuleInterceptor {

    /**
     * 根据[rule]判断是否匹配拦截规则
     */
    fun match(rule: Rule): Boolean

    /**
     * 拦截[rule], 返回查询条件
     */
    fun intercept(rule: Rule, context: QueryContext): Criteria
}
