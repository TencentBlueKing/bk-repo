/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import io.agentscope.core.agui.event.AguiEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentRunEventSupportTest {

    @Test
    fun `RunFinished and RunError are terminal`() {
        assertTrue(
            AgentRunEventSupport.isTerminal(
                AguiEvent.RunFinished("t", "r", null, AguiEvent.RunFinishedSuccessOutcome()),
            ),
        )
        assertTrue(
            AgentRunEventSupport.isTerminal(
                AguiEvent.RunError("t", "r", "boom", "boom"),
            ),
        )
    }

    @Test
    fun `RunStarted is not terminal`() {
        assertFalse(
            AgentRunEventSupport.isTerminal(
                AguiEvent.RunStarted("t", "r", null, null),
            ),
        )
    }
}
