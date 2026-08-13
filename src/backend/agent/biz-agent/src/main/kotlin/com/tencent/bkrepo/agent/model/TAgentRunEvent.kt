/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDateTime

/**
 * AG-UI run 事件持久化，对应 Mongo 集合 `agent_run_event`。
 *
 * [eventData] 保存 [io.agentscope.core.agui.encoder.AguiEventEncoder] 的标准 JSON，
 * 回放时由 [com.tencent.bkrepo.agent.service.run.AguiSseEventWriter] 包装为 SSE。
 */
@Document("agent_run_event")
@CompoundIndexes(
    CompoundIndex(
        name = "runId_eventIndex_idx",
        def = "{'runId': 1, 'eventIndex': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "scope_run_eventIndex_idx",
        def = "{'userId': 1, 'projectId': 1, 'threadId': 1, 'runId': 1, 'eventIndex': 1}",
        background = true,
    ),
    CompoundIndex(
        name = "threadId_createdAt_idx",
        def = "{'threadId': 1, 'createdAt': 1}",
        background = true,
    ),
)
data class TAgentRunEvent(
    var id: String? = null,
    var userId: String,
    var projectId: String,
    var threadId: String,
    var runId: String,
    var agentId: String? = null,
    var parentAgentId: String? = null,
    var delegationId: String? = null,
    var agentExecutionId: String? = null,
    var eventIndex: Long,
    var eventType: String,
    /** AguiEventEncoder 输出的标准 JSON payload。 */
    var eventData: String,
    var terminal: Boolean = false,
    var createdAt: LocalDateTime,
    @Indexed(expireAfter = "0s", background = true)
    var expiresAt: Instant,
)
