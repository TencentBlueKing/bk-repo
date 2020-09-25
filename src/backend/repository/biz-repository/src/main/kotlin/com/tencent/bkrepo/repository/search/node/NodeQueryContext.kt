package com.tencent.bkrepo.repository.search.node

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.search.common.CommonQueryContext
import org.springframework.data.mongodb.core.query.Query

class NodeQueryContext(
    override var queryModel: QueryModel,
    override val mongoQuery: Query,
    override val interpreter: MongoQueryInterpreter
): CommonQueryContext(queryModel, mongoQuery, interpreter) {
    var selectMetadata: Boolean = false
    var selectStageTag: Boolean = false
}