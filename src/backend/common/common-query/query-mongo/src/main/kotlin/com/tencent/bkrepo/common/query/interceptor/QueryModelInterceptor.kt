package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.model.QueryModel

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
interface QueryModelInterceptor {

    fun intercept(queryModel: QueryModel): QueryModel
}
