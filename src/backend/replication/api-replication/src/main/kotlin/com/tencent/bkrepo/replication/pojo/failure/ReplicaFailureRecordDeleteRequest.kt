package com.tencent.bkrepo.replication.pojo.failure

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 同步失败记录删除请求
 */
@Schema(title = "同步失败记录删除请求")
data class ReplicaFailureRecordDeleteRequest(
    @get:Schema(title = "记录ID列表")
    val ids: List<String>? = null,
    @get:Schema(title = "最大重试次数（删除重试次数超过此值的记录）")
    val maxRetryCount: Int? = null
)

