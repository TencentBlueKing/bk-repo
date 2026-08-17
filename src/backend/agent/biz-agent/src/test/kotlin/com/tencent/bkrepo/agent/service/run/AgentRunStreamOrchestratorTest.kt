/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.hitl.DefaultAgentInterruptStateRepository
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.model.TAgentRunEvent
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunTriggerType
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.runtime.AgentRunReplaySinkRegistry
import com.tencent.bkrepo.agent.runtime.store.InMemoryActiveRunStateStore
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.agent.session.InMemoryAgentPendingInterruptStore
import com.tencent.bkrepo.agent.session.InMemoryAgentResumeIdempotencyStore
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import io.agentscope.core.state.InMemoryAgentStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentRunStreamOrchestratorTest {

    private val properties = EffectiveAgentRuntimeProperties.defaults().copy(
        reconnectPollInterval = java.time.Duration.ofMillis(50),
        reconnectTimeout = java.time.Duration.ofMillis(200),
    )
    private val sseEventWriter = AguiSseEventWriter()
    private val replaySinkRegistry = AgentRunReplaySinkRegistry()

    @Test
    fun `stream rejects run not belonging to thread`() {
        val orchestrator = orchestrator(
            runs = mapOf(
                "run-1" to sampleRun("run-1", "thread-other"),
            ),
        )
        assertThrows(ParameterInvalidException::class.java) {
            orchestrator.stream("user-1", "project-1", "thread-1", "run-1", null)
        }
    }

    @Test
    fun `stream resolves latest run when runId omitted`() {
        val run = sampleRun("run-latest", "thread-1", AgentRunStatus.COMPLETED)
        val replayLatch = CountDownLatch(1)
        val orchestrator = orchestrator(
            runs = mapOf("run-latest" to run),
            latestByThread = run,
            eventService = FakeRunEventService(hasEvents = true, onReplay = { replayLatch.countDown() }),
        )
        orchestrator.stream("user-1", "project-1", "thread-1", null, null)
        assertTrue(replayLatch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `replayTerminalForRun uses event replay when persisted events exist`() {
        val run = sampleRun("run-1", "thread-1", AgentRunStatus.COMPLETED)
        val replayLatch = CountDownLatch(1)
        val orchestrator = orchestrator(
            runs = mapOf("run-1" to run),
            eventService = FakeRunEventService(hasEvents = true, onReplay = { replayLatch.countDown() }),
        )
        val input = RunAgentInput.builder()
            .threadId("thread-1")
            .runId("run-1")
            .build()
        orchestrator.replayTerminalForRun(input, run)
        assertTrue(replayLatch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `stream replays persisted events for terminal run`() {
        val run = sampleRun("run-1", "thread-1", AgentRunStatus.COMPLETED)
        val replayLatch = CountDownLatch(1)
        val eventService = FakeRunEventService(hasEvents = true, onReplay = { replayLatch.countDown() })
        val orchestrator = orchestrator(
            runs = mapOf("run-1" to run),
            eventService = eventService,
        )
        orchestrator.stream("user-1", "project-1", "thread-1", "run-1", 0L)
        assertTrue(replayLatch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("run-1"), eventService.replayCalls)
    }

    private fun orchestrator(
        runs: Map<String, TAgentRun> = emptyMap(),
        latestByThread: TAgentRun? = null,
        eventService: FakeRunEventService = FakeRunEventService(),
    ): AgentRunStreamOrchestrator {
        val activeRunManager = ActiveRunManager(
            stateStore = InMemoryActiveRunStateStore(),
            agentStateStore = InMemoryAgentStateStore(),
            eventPublisher = ApplicationEventPublisher { },
        )
        return AgentRunStreamOrchestrator(
            properties = properties,
            agentRunRecordService = FakeRunRecordService(runs, latestByThread),
            activeRunManager = activeRunManager,
            runEventService = eventService,
            replaySinkRegistry = replaySinkRegistry,
            interruptStateRepository = DefaultAgentInterruptStateRepository(
                pendingInterruptStore = InMemoryAgentPendingInterruptStore(),
                resumeIdempotencyStore = InMemoryAgentResumeIdempotencyStore(),
            ),
            sseEventWriter = sseEventWriter,
        )
    }

    private fun sampleRun(
        runId: String,
        threadId: String,
        status: AgentRunStatus = AgentRunStatus.RUNNING,
    ): TAgentRun = TAgentRun(
        runId = runId,
        threadId = threadId,
        userId = "user-1",
        projectId = "project-1",
        status = status,
        triggerType = AgentRunTriggerType.USER_INPUT,
        startedAt = LocalDateTime.now(),
    )

    private class FakeRunRecordService(
        private val runs: Map<String, TAgentRun>,
        private val latestByThread: TAgentRun?,
    ) : AgentRunRecordService {
        override fun findByRunId(runId: String): TAgentRun? = runs[runId]
        override fun findLatestByThreadId(threadId: String): TAgentRun? = latestByThread
        override fun startRun(
            runId: String,
            executionId: String,
            threadId: String,
            userId: String,
            projectId: String,
            deviceId: String?,
            entryAgentId: String,
            triggerType: AgentRunTriggerType,
        ) = Unit
        override fun finishRun(
            runId: String,
            status: AgentRunStatus,
            cancelReason: String?,
            errorCode: String?,
        ) = Unit
        override fun removeByThreadId(threadId: String) = Unit
    }

    private class FakeRunEventService(
        private val hasEvents: Boolean = false,
        private val onReplay: (() -> Unit)? = null,
    ) : AgentRunEventService {
        val replayCalls = mutableListOf<String>()

        override fun append(scope: AgentRunScope, event: AguiEvent): Long = 0L
        override fun hasEvents(runId: String): Boolean = hasEvents
        override fun listEvents(runId: String): List<TAgentRunEvent> = emptyList()
        override fun listAfterIndex(runId: String, afterIndex: Long): List<TAgentRunEvent> = emptyList()
        override fun replayTo(emitter: SseEmitter, runId: String, afterIndex: Long): Long {
            replayCalls.add(runId)
            onReplay?.invoke()
            return afterIndex
        }
        override fun closeRun(runId: String) = Unit
        override fun cleanup(runId: String) = Unit
    }
}
