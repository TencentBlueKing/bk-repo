package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.model.QueryModel

/**
 * 查询模型拦截器
 */
interface QueryModelInterceptor {

    fun intercept(queryModel: QueryModel, context: QueryContext): QueryModel
}
