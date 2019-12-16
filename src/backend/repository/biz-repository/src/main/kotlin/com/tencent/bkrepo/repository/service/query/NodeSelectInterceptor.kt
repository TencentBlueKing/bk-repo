package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.interceptor.QueryModelInterceptor
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.model.TNode

/**
 * 排除指定字段
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
class NodeSelectInterceptor : QueryModelInterceptor {

    private val constraintProperties = listOf("_id", "_class", TNode::deleted.name)

    override fun intercept(queryModel: QueryModel): QueryModel {
        queryModel.select?.let {
            for (constraint in constraintProperties) {
                if (it.contains(constraint)) {
                    it.remove(constraint)
                }
            }
        }
        return queryModel
    }
}
