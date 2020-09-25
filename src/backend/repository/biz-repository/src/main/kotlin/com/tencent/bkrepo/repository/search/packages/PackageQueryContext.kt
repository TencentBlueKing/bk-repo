package com.tencent.bkrepo.repository.search.packages

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.search.common.CommonQueryContext
import org.springframework.data.mongodb.core.query.Query

class PackageQueryContext(
    override var queryModel: QueryModel,
    override val mongoQuery: Query,
    override val interpreter: MongoQueryInterpreter
): CommonQueryContext(queryModel, mongoQuery, interpreter)