package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryModelInterceptor
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.model.TNode

/**
 * 节点自定义查询规则拦截器
 *
 * 1. 校验node query model的格式
 * 2. 添加deleted属性为null的查询条件
 */
class NodeQueryInterceptor : QueryModelInterceptor {

    override fun intercept(queryModel: QueryModel): QueryModel {
        validateModel(queryModel)
        setDeletedNull(queryModel)
        filterRootNode(queryModel)

        return queryModel
    }

    /**
     * 校验[queryModel]格式，查询条件必须满足以下格式：
     *   1. rule必须为AND类型的嵌套查询
     *   2. rule嵌套查询规则列表中，必须指定projectId条件，且为EQ操作
     *   3. rule嵌套查询规则列表中，必须指定repoName条件，且为EQ 或者 IN操作
     * 对于rule嵌套查询规则列表中的其它规则，不做限定
     */
    private fun validateModel(queryModel: QueryModel) {
        val rule = queryModel.rule
        // rule必须为AND类型的嵌套查询
        if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
            var projectIdValidation = false
            var repoNameValidation = false
            for (subRule in rule.rules) {
                if (checkProjectId(subRule)) projectIdValidation = true
                if (checkRepoName(subRule)) repoNameValidation = true
            }
            if (!projectIdValidation) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "projectId")
            }
            if (!repoNameValidation) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "repoName")
            }
        } else {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "relation")
        }
    }

    /**
     * [rule]嵌套查询规则列表中，必须指定`projectId`条件，且为`EQ`操作
     */
    private fun checkRepoName(rule: Rule): Boolean {
        if (rule is Rule.QueryRule && rule.field == TNode::repoName.name) {
            if (rule.operation == OperationType.EQ || rule.operation == OperationType.IN) {
                return true
            }
        }
        return false
    }

    /**
     * [rule]嵌套查询规则列表中，必须指定`repoName`条件，且为`EQ`或者`IN`操作
     */
    private fun checkProjectId(rule: Rule): Boolean {
        if (rule is Rule.QueryRule && rule.field == TNode::projectId.name) {
            if (rule.operation == OperationType.EQ && rule.value.toString().isNotBlank()) {
                return true
            }
        }
        return false
    }

    /**
     * 添加deleted属性为null的查询条件到[queryModel]中
     */
    private fun setDeletedNull(queryModel: QueryModel) {
        queryModel.addQueryRule(Rule.QueryRule(TNode::deleted.name, "", OperationType.NULL))
    }

    /**
     * 添加name属性不为空的查询条件到[queryModel]中来过滤根节点
     */
    private fun filterRootNode(queryModel: QueryModel) {
        queryModel.addQueryRule(Rule.QueryRule(TNode::name.name, "", OperationType.NE))
    }
}
