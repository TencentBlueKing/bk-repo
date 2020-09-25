package com.tencent.bkrepo.repository.search.common

import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryModelInterceptor
import com.tencent.bkrepo.common.query.model.QueryModel

/**
 * 用户排除指定字段
 */
open class SelectFieldInterceptor : QueryModelInterceptor {

    override fun intercept(queryModel: QueryModel, context: QueryContext): QueryModel {
        val newSelect = queryModel.select?.toMutableList()
        newSelect?.let {
            for (constraint in getConstraintFields()) {
                it.remove(constraint)
            }
        }
        queryModel.select = newSelect
        return queryModel
    }

    open fun getConstraintFields(): MutableList<String> {
        return mutableListOf("_id", "_class")
    }
}
