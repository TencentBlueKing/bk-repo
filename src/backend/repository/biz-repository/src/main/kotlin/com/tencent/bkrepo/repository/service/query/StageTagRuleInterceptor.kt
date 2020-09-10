package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.constant.METADATA_PREFIX
import com.tencent.bkrepo.repository.constant.SystemMetadata
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.data.mongodb.core.query.Criteria

/**
 * 晋级标签规则拦截器
 *
 * 条件构造器中传入条件是`stageTag`，需要转换成对应元数据查询条件
 */
class StageTagRuleInterceptor : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field == NodeInfo::stageTag.name
    }

    override fun intercept(rule: Rule, context: MongoQueryInterpreter): Criteria {
        with(rule as Rule.QueryRule) {
            val queryRule = Rule.QueryRule(METADATA_PREFIX + SystemMetadata.STAGE.key, value, operation)
            return context.resolveRule(queryRule)
        }
    }
}
