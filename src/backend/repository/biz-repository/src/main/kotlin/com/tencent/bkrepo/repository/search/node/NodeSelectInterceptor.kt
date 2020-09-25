package com.tencent.bkrepo.repository.search.node

import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.search.common.SelectFieldInterceptor

/**
 * 用户排除指定字段
 */
class NodeSelectInterceptor : SelectFieldInterceptor() {

    override fun intercept(queryModel: QueryModel, context: QueryContext): QueryModel {
        val newQueryModel = super.intercept(queryModel, context)
        with(context as NodeQueryContext) {
            selectMetadata = newQueryModel.select?.contains(NodeInfo::metadata.name) ?: true
            selectStageTag = newQueryModel.select?.contains(NodeInfo::stageTag.name) ?: true
            if (selectStageTag || selectMetadata) {
                val newSelect = newQueryModel.select?.toMutableList()
                newSelect?.add(NodeInfo::metadata.name)
                newQueryModel.select = newSelect
            }
        }
        return newQueryModel
    }

    override fun getConstraintFields(): MutableList<String> {
        return super.getConstraintFields().apply {
            add(TNode::deleted.name)
        }
    }
}
