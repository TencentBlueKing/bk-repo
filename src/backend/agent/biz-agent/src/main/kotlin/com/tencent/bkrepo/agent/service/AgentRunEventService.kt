/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service

import com.tencent.bkrepo.agent.model.TAgentRunEvent
import com.tencent.bkrepo.agent.service.run.AgentRunScope
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** AG-UI run 事件事实源：append、回放与清理。 */
interface AgentRunEventService {

    /** 持久化单个 AG-UI 事件，返回分配的 eventIndex。 */
    fun append(scope: AgentRunScope, event: AguiEvent): Long

    fun hasEvents(runId: String): Boolean

    fun listEvents(runId: String): List<TAgentRunEvent>

    fun listAfterIndex(runId: String, afterIndex: Long): List<TAgentRunEvent>

    /** 将已持久化事件按序写入 SSE，返回最后写入的 eventIndex（无事件时返回 afterIndex）。 */
    fun replayTo(emitter: SseEmitter, runId: String, afterIndex: Long = -1L): Long

    fun closeRun(runId: String)

    fun cleanup(runId: String)
}
