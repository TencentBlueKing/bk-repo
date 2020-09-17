package com.tencent.bkrepo.common.query.interceptor

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.QueryModel
import org.springframework.data.mongodb.core.query.Query

open class QueryContext(
    open var queryModel: QueryModel,
    open val mongoQuery: Query,
    open val interpreter: MongoQueryInterpreter
)