package com.tencent.bkrepo.common.mongo.routing.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 补偿后校验不一致记录（写入 mongo_inconsistency_log）。
 * ponytail: 替代 Document().apply { put(...) } 的手写模式。
 */
@Document(collection = "mongo_inconsistency_log")
data class InconsistencyRecord(
    val ruleName: String?,
    val routingKey: String?,
    val collectionName: String?,
    val primaryKey: String?,
    val operationType: String?,
    val reason: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)