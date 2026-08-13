/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import com.tencent.bkrepo.agent.runtime.store.InMemoryActiveRunStateStore
import io.agentscope.core.state.InMemoryAgentStateStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class ActiveRunManagerTest {

    private val stateStore = InMemoryActiveRunStateStore()
    private val manager = ActiveRunManager(
        stateStore = stateStore,
        agentStateStore = InMemoryAgentStateStore(),
        eventPublisher = ApplicationEventPublisher { },
    )

    @Test
    fun `pending stop applies when handle registers later`() {
        val scope = ActiveRunScope("user-1", "project-1", "thread-1")
        var aborted = false
        manager.requestStop(scope, "run-1")
        manager.registerHandle(scope, "run-1", runtimeContext = stubRuntimeContext()) {
            aborted = true
        }
        assertTrue(aborted)
    }

    @Test
    fun `releaseRun only clears matching active runId`() {
        val scope = ActiveRunScope("user-1", "project-1", "thread-1")
        assertTrue(manager.tryAcquire(scope))
        manager.bindActiveRun(scope, "run-old")
        manager.releaseRun(scope, "run-new")
        assertTrue(manager.isRunning(scope))
        assertTrue(manager.getActiveRunId(scope) == "run-old")
        manager.releaseRun(scope, "run-old")
        assertFalse(manager.isRunning(scope))
    }

    private fun stubRuntimeContext(): io.agentscope.core.agent.RuntimeContext {
        return io.agentscope.core.agent.RuntimeContext.builder()
            .userId("user-1")
            .sessionId("thread-1")
            .build()
    }
}
