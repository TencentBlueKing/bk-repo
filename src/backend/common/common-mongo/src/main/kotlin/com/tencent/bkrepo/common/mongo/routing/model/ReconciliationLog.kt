package com.tencent.bkrepo.common.mongo.routing.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 旁路对账日志（写入 node_reconciliation_log）。
 * ponytail: 替代 Document().apply { put(...) } 的手写模式。
 */
@Document(collection = "node_reconciliation_log")
data class ReconciliationLog(
    val projectId: String,
    val checkType: String,
    val passed: Boolean,
    val detail: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)