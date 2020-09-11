package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.service.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

/**
 * 仓库类型规则拦截器
 *
 * 条件构造器中传入条件是`repoType`，需要转换成对应的仓库列表
 */
@Component
class RepoTypeRuleInterceptor : QueryRuleInterceptor {

    @Autowired
    private lateinit var repositoryService: RepositoryService

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field == "repoType"
    }

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        with(rule as Rule.QueryRule) {
            val projectId = (context as NodeQueryContext).findProjectId()
            val repoNameList = repositoryService.list(projectId, name = null, type = value.toString())
                .apply { context.repoList = this }
                .map { it.name }
            val queryRule = Rule.QueryRule(NodeInfo::repoName.name, repoNameList, OperationType.IN)
            return context.interpreter.resolveRule(queryRule, context)
        }
    }
}
