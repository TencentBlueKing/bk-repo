package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.model.QueryModel

interface QueryModelInterceptor {

    fun intercept(queryModel: QueryModel): QueryModel
}
