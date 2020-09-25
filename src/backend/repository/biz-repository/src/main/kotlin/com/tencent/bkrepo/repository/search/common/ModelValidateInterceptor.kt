package com.tencent.bkrepo.repository.search.common

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryModelInterceptor
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule

/**
 * 规则验证
 */
open class ModelValidateInterceptor : QueryModelInterceptor {

    override fun intercept(queryModel: QueryModel, context: QueryContext): QueryModel {
        // 校验query model的格式
        validateModel(queryModel)

        return queryModel
    }

    /**
     * 校验[queryModel]格式，查询条件必须满足以下格式：
     *   1. rule必须为AND类型的嵌套查询
     *   2. rule嵌套查询规则列表中，必须指定projectId条件，且为EQ操作
     *   对于rule嵌套查询规则列表中的其它规则，不做限定
     */
    private fun validateModel(queryModel: QueryModel) {
        val rule = queryModel.rule
        // rule必须为AND类型的嵌套查询
        if (rule !is Rule.NestedRule || rule.relation != Rule.NestedRule.RelationType.AND) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "relation")
        }
    }

}