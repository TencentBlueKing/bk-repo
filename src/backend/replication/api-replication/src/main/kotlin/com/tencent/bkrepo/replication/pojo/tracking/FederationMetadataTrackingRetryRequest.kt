package com.tencent.bkrepo.replication.pojo.tracking

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦元数据跟踪记录重试请求
 */
@Schema(title = "联邦元数据跟踪记录重试请求")
data class FederationMetadataTrackingRetryRequest(
    @get:Schema(title = "记录ID", required = true)
    val id: String
)

