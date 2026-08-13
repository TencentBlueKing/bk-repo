/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import io.agentscope.core.agui.event.AguiEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class AgentRunOutcomeTrackerTest {

    private val tracker = AgentRunOutcomeTracker(
        aguiInterruptTracker = com.tencent.bkrepo.agent.agui.AguiInterruptTracker(),
        pendingInterruptStore = com.tencent.bkrepo.agent.session.InMemoryAgentPendingInterruptStore(),
    )

    @Test
    fun `RunError marks terminal status as FAILED`() {
        val status = AtomicReference(AgentRunStatus.COMPLETED)
        tracker.applyTerminalEvent(
            AguiEvent.RunError("thread-1", "run-1", "boom", "boom"),
            status,
        )
        assertEquals(AgentRunStatus.FAILED, status.get())
    }

    @Test
    fun `RunFinished success marks COMPLETED`() {
        val status = AtomicReference(AgentRunStatus.COMPLETED)
        tracker.applyTerminalEvent(
            AguiEvent.RunFinished(
                "thread-1",
                "run-1",
                null,
                AguiEvent.RunFinishedSuccessOutcome(),
            ),
            status,
        )
        assertEquals(AgentRunStatus.COMPLETED, status.get())
    }

    @Test
    fun `RunFinished after RunError keeps FAILED`() {
        val status = AtomicReference(AgentRunStatus.COMPLETED)
        tracker.applyTerminalEvent(
            AguiEvent.RunError("thread-1", "run-1", "boom", "boom"),
            status,
        )
        tracker.applyTerminalEvent(
            AguiEvent.RunFinished(
                "thread-1",
                "run-1",
                null,
                AguiEvent.RunFinishedSuccessOutcome(),
            ),
            status,
        )
        assertEquals(AgentRunStatus.FAILED, status.get())
    }
}
