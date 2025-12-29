package com.tencent.bkrepo.replication.pojo.event

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 事件记录重试请求
 */
@Schema(title = "事件记录重试请求")
data class EventRecordRetryRequest(
    @get:Schema(title = "事件ID")
    val eventId: String? = null,
    @get:Schema(title = "任务Key")
    val taskKey: String? = null
)

