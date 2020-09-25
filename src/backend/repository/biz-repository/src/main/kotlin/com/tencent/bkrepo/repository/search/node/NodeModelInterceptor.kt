package com.tencent.bkrepo.repository.search.node

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.search.common.ModelValidateInterceptor

/**
 * 节点自定义查询规则拦截器
 */
class NodeModelInterceptor : ModelValidateInterceptor() {

    override fun intercept(queryModel: QueryModel, context: QueryContext): QueryModel {
        super.intercept(queryModel, context)
        // 添加deleted属性为null的查询条件
        setDeletedNull(queryModel)
        return queryModel
    }

    /**
     * 添加deleted属性为null的查询条件到[queryModel]中
     */
    private fun setDeletedNull(queryModel: QueryModel) {
        queryModel.addQueryRule(Rule.QueryRule(TNode::deleted.name, StringPool.EMPTY, OperationType.NULL))
    }
}
