package com.tencent.bkrepo.common.mongo.routing.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingCollections

/**
 * 补偿后校验不一致记录（写入 [MongoRoutingCollections.INCONSISTENCY]）。
 */
@Document(collection = MongoRoutingCollections.INCONSISTENCY)
data class InconsistencyRecord(
    val ruleName: String?,
    val routingKey: String?,
    val collectionName: String?,
    val primaryKey: String?,
    val operationType: String?,
    val reason: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)