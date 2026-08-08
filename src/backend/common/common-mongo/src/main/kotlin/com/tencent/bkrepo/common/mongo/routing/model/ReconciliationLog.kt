package com.tencent.bkrepo.common.mongo.routing.model

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingCollections
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 双写旁路对账日志（写入 [MongoRoutingCollections.RECONCILIATION]）。
 */
@Document(collection = MongoRoutingCollections.RECONCILIATION)
data class ReconciliationLog(
    val projectId: String,
    val ruleName: String = "node",
    val checkType: String,
    val passed: Boolean,
    val detail: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)