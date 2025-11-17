package com.tencent.bkrepo.replication.pojo.failure

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 同步失败记录重试请求
 */
@Schema(title = "同步失败记录重试请求")
data class ReplicaFailureRecordRetryRequest(
    @get:Schema(title = "记录ID", required = true)
    val id: String
)

