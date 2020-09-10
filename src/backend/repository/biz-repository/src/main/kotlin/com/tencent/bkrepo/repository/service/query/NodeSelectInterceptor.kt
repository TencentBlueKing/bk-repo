package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.interceptor.QueryModelInterceptor
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeInfo

/**
 * 用户排除指定字段
 */
class NodeSelectInterceptor : QueryModelInterceptor {

    /**
     * 限制查询字段，自动排除查询条件中含有该列表字段的查询规则
     */
    private val constraintProperties = listOf("_id", "_class", TNode::deleted.name)

    override fun intercept(queryModel: QueryModel): QueryModel {
        val newSelect = queryModel.select?.toMutableList()
        newSelect?.let {
            for (constraint in constraintProperties) {
                it.remove(constraint)
            }
        }
        // 如果指定了stageTag，添加metadata字段
        if (newSelect?.contains(NodeInfo::stageTag.name) == true && !newSelect.contains(NodeInfo::metadata.name)) {
            newSelect.add(NodeInfo::metadata.name)
        }
        queryModel.select = newSelect
        return queryModel
    }
}
