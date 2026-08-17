/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.hitl

import com.tencent.bkrepo.agent.session.PendingInterruptSession
import com.tencent.bkrepo.agent.session.PendingInterruptSnapshot
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.stereotype.Component

/**
 * 跟踪 TOOL_CALL 与 RunFinished interrupt，供 pending interrupt 持久化。
 *
 * 本类是单例 Spring bean，同一实例会被所有并发 SSE run 共享，因此状态必须以 [State] 的形式
 * 由调用方每次 run 各自持有，不能存为本类的实例字段（否则并发 run 之间会互相污染 toolCallId 映射）。
 */
@Component
class AguiInterruptTracker {

    /** 单次 run 的 toolCallId -> toolName 映射，随 run 生命周期由调用方创建与持有。 */
    class State {
        val toolNameByCallId = mutableMapOf<String, String>()
    }

    fun onEvent(event: AguiEvent, state: State) {
        when (event) {
            is AguiEvent.ToolCallStart -> {
                state.toolNameByCallId[event.toolCallId()] = event.toolCallName()
            }
            else -> Unit
        }
    }

    fun captureSuspendedSession(runId: String, event: AguiEvent.RunFinished, state: State): PendingInterruptSession? {
        val outcome = event.outcome()
        if (outcome !is AguiEvent.RunFinishedInterruptOutcome) {
            return null
        }
        val interrupts = outcome.interrupts().mapNotNull { interrupt -> toSnapshot(interrupt, state) }
        if (interrupts.isEmpty()) {
            return null
        }
        return PendingInterruptSession(originRunId = runId, interrupts = interrupts)
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSnapshot(interrupt: AguiEvent.Interrupt, state: State): PendingInterruptSnapshot? {
        val id = interrupt.id()?.takeIf { it.isNotBlank() } ?: return null
        val toolCallId = interrupt.toolCallId()?.takeIf { it.isNotBlank() }
        val toolName = toolCallId?.let { state.toolNameByCallId[it] }
        val responseSchema = interrupt.responseSchema() as? Map<String, Any?>
        val requiresApproval = hasApprovedSchema(responseSchema)
        return PendingInterruptSnapshot(
            id = id,
            reason = interrupt.reason().orEmpty(),
            toolCallId = toolCallId,
            toolName = toolName,
            requiresApproval = requiresApproval,
            message = interrupt.message(),
            responseSchema = responseSchema,
            expiresAt = interrupt.expiresAt(),
            metadata = interrupt.metadata() as? Map<String, Any?>,
        )
    }

    private fun hasApprovedSchema(responseSchema: Any?): Boolean {
        if (responseSchema !is Map<*, *>) return false
        val properties = responseSchema["properties"] as? Map<*, *> ?: return false
        return properties.containsKey("approved")
    }
}
