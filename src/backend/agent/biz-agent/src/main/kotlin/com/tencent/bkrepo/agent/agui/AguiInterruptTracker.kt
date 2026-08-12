/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import com.tencent.bkrepo.agent.session.PendingInterruptSession
import com.tencent.bkrepo.agent.session.PendingInterruptSnapshot
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.stereotype.Component

/** 跟踪 TOOL_CALL 与 RunFinished interrupt，供 pending interrupt 持久化。 */
@Component
class AguiInterruptTracker {

    private val toolNameByCallId = mutableMapOf<String, String>()

    fun reset() {
        toolNameByCallId.clear()
    }

    fun onEvent(event: AguiEvent) {
        when (event) {
            is AguiEvent.ToolCallStart -> {
                toolNameByCallId[event.toolCallId()] = event.toolCallName()
            }
            else -> Unit
        }
    }

    fun captureSuspendedSession(runId: String, event: AguiEvent.RunFinished): PendingInterruptSession? {
        val outcome = event.outcome()
        if (outcome !is AguiEvent.RunFinishedInterruptOutcome) {
            return null
        }
        val interrupts = outcome.interrupts().mapNotNull { interrupt -> toSnapshot(interrupt) }
        if (interrupts.isEmpty()) {
            return null
        }
        return PendingInterruptSession(originRunId = runId, interrupts = interrupts)
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSnapshot(interrupt: AguiEvent.Interrupt): PendingInterruptSnapshot? {
        val id = interrupt.id()?.takeIf { it.isNotBlank() } ?: return null
        val toolCallId = interrupt.toolCallId()?.takeIf { it.isNotBlank() }
        val toolName = toolCallId?.let { toolNameByCallId[it] }
        val requiresApproval = hasApprovedSchema(interrupt.responseSchema())
        return PendingInterruptSnapshot(
            id = id,
            reason = interrupt.reason().orEmpty(),
            toolCallId = toolCallId,
            toolName = toolName,
            requiresApproval = requiresApproval,
        )
    }

    private fun hasApprovedSchema(responseSchema: Any?): Boolean {
        if (responseSchema !is Map<*, *>) return false
        val properties = responseSchema["properties"] as? Map<*, *> ?: return false
        return properties.containsKey("approved")
    }
}
