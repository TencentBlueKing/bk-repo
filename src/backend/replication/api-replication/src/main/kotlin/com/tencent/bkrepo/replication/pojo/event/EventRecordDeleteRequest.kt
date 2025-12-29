package com.tencent.bkrepo.replication.pojo.event

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 事件记录删除请求
 */
@Schema(title = "事件记录删除请求")
data class EventRecordDeleteRequest(
    @get:Schema(title = "事件ID")
    val eventId: String? = null,
    @get:Schema(title = "任务Key")
    val taskKey: String? = null
)

