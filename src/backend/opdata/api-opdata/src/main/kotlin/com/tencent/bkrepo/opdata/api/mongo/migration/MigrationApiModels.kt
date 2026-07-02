package com.tencent.bkrepo.opdata.api.mongo.migration

import com.tencent.bkrepo.common.mongo.api.routing.HistoricalSyncStrategy
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase

data class MigrationBindingRequest(
    val ruleName: String,
    val projectId: String,
    val targetInstance: String,
    val historicalSyncStrategy: HistoricalSyncStrategy = HistoricalSyncStrategy.JOB_ONLY,
    val businessId: String? = null,
    /** Tier-Biz：与 [businessId] 同时提供时批量绑定组内项目 */
    val groupProjectIds: List<String>? = null,
)

data class MigrationProjectRequest(
    val ruleName: String,
    val projectId: String,
)

data class MigrationStatusResponse(
    val projectId: String,
    val ruleName: String,
    val phase: MigrationPhase,
    val targetInstance: String?,
    val lastError: String?,
    val updatedAt: String?,
)

data class MigrationStatusListResponse(
    val projects: List<MigrationStatusResponse>,
)
