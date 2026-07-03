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
    /** CATCH_UP 延迟（秒），仅 CATCH_UP/VERIFY 阶段有意义 */
    val catchUpLagSeconds: Long? = null,
    /** sync_failed 队列未清零条数 */
    val syncFailedCount: Long = 0L,
    /** 补偿队列 PENDING 条数 */
    val compensationPendingCount: Long? = null,
)

data class MigrationStatusListResponse(
    val projects: List<MigrationStatusResponse>,
)
