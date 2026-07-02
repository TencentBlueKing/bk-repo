package com.tencent.bkrepo.common.mongo.api.routing

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

data class BatchQueryGroup(
    val instanceId: String,
    val template: MongoTemplate,
    val collectionNames: List<String>,
    val criteriaCustomizer: (Query) -> Query,
)
