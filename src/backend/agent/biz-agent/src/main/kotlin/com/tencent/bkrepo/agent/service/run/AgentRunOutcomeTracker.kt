/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.agui.AguiInterruptTracker
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/** 从 AG-UI 终态事件推导 run 状态，并维护 pending interrupt 快照。 */
@Component
class AgentRunOutcomeTracker(
    private val aguiInterruptTracker: AguiInterruptTracker,
    private val pendingInterruptStore: AgentPendingInterruptStore,
) {

    fun applyTerminalEvent(event: AguiEvent, terminalStatus: AtomicReference<AgentRunStatus>) {
        when (event) {
            is AguiEvent.RunFinished -> {
                // 2.0.1 legacy：errorEvents() 可能在 RUN_ERROR 后再发 RUN_FINISHED，终态以 FAILED 为准。
                if (terminalStatus.get() == AgentRunStatus.FAILED) return
                terminalStatus.set(
                    if (event.outcome() is AguiEvent.RunFinishedInterruptOutcome) {
                        AgentRunStatus.SUSPENDED
                    } else {
                        AgentRunStatus.COMPLETED
                    },
                )
            }
            is AguiEvent.RunError -> terminalStatus.set(AgentRunStatus.FAILED)
            else -> Unit
        }
    }

    fun capturePendingInterruptIfNeeded(
        threadId: String,
        runId: String,
        event: AguiEvent,
        terminalStatus: AgentRunStatus,
        interruptState: AguiInterruptTracker.State,
    ) {
        if (event !is AguiEvent.RunFinished) return
        when (terminalStatus) {
            AgentRunStatus.SUSPENDED -> {
                aguiInterruptTracker.captureSuspendedSession(runId, event, interruptState)?.let { session ->
                    pendingInterruptStore.save(threadId, session)
                }
            }
            AgentRunStatus.COMPLETED -> pendingInterruptStore.clear(threadId)
            else -> Unit
        }
    }
}
