/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.runtime.AgentRunReplaySinkRegistry
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * reconnect 编排：终态全量回放、active run 增量回放 + 本地 sink / 跨实例 Mongo 轮询。
 */
@Component
class AgentRunReconnectOrchestrator(
    private val properties: EffectiveAgentRuntimeProperties,
    private val agentRunRecordService: AgentRunRecordService,
    private val activeRunManager: ActiveRunManager,
    private val runEventService: AgentRunEventService,
    private val replaySinkRegistry: AgentRunReplaySinkRegistry,
    private val terminalRunReplayer: TerminalRunReplayer,
    private val sseEventWriter: AguiSseEventWriter,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun reconnect(
        userId: String,
        projectId: String,
        request: AgentRunReconnectRequest,
        existingRun: TAgentRun,
    ): SseEmitter {
        val runScope = ActiveRunScope(userId, projectId, request.threadId)
        val stillRunning = existingRun.status == AgentRunStatus.RUNNING || activeRunManager.isRunning(runScope)
        if (!stillRunning) {
            val input = reconnectInput(request)
            return terminalRunReplayer.replay(input, existingRun)
        }
        return streamActiveRun(userId, projectId, request, existingRun, runScope)
    }

    private fun streamActiveRun(
        userId: String,
        projectId: String,
        request: AgentRunReconnectRequest,
        existingRun: TAgentRun,
        runScope: ActiveRunScope,
    ): SseEmitter {
        logger.info("live reconnect run[{}] thread[{}]", request.runId, request.threadId)
        val emitter = SseEmitter(properties.reconnectTimeout.toMillis())
        val afterIndex = request.lastEventIndex ?: -1L
        val finished = AtomicBoolean(false)

        Schedulers.boundedElastic().schedule {
            try {
                var cursor = runEventService.replayTo(emitter, request.runId, afterIndex)
                if (attachLocalSink(request, cursor, emitter, finished)) {
                    return@schedule
                }
                pollRemoteEvents(request, runScope, cursor, emitter, finished)
            } catch (ex: Exception) {
                if (finished.compareAndSet(false, true)) {
                    emitter.completeWithError(ex)
                }
            }
        }
        return emitter
    }

    private fun attachLocalSink(
        request: AgentRunReconnectRequest,
        cursor: Long,
        emitter: SseEmitter,
        finished: AtomicBoolean,
    ): Boolean {
        val unsubscribe = replaySinkRegistry.subscribe(request.runId, cursor) { event ->
            if (finished.get()) return@subscribe
            try {
                sseEventWriter.sendJson(emitter, event.eventJson)
                if (event.terminal && finished.compareAndSet(false, true)) {
                    emitter.complete()
                }
            } catch (ex: Exception) {
                if (finished.compareAndSet(false, true)) {
                    emitter.completeWithError(ex)
                }
            }
        } ?: return false

        emitter.onCompletion { unsubscribe() }
        emitter.onTimeout {
            unsubscribe()
            if (finished.compareAndSet(false, true)) {
                sendReconnectTimeout(emitter, request)
            }
        }
        emitter.onError { unsubscribe() }
        return true
    }

    private fun pollRemoteEvents(
        request: AgentRunReconnectRequest,
        runScope: ActiveRunScope,
        startCursor: Long,
        emitter: SseEmitter,
        finished: AtomicBoolean,
    ) {
        var cursor = startCursor
        val deadline = System.currentTimeMillis() + properties.reconnectTimeout.toMillis()
        while (!finished.get() && System.currentTimeMillis() < deadline) {
            val events = runEventService.listAfterIndex(request.runId, cursor)
            for (event in events) {
                if (finished.get()) return
                sseEventWriter.sendJson(emitter, event.eventData)
                cursor = event.eventIndex
                if (event.terminal) {
                    finished.set(true)
                    emitter.complete()
                    return
                }
            }
            val latestRun = agentRunRecordService.findByRunId(request.runId)
            if (latestRun?.status != AgentRunStatus.RUNNING && !activeRunManager.isRunning(runScope)) {
                finished.set(true)
                emitter.complete()
                return
            }
            Thread.sleep(properties.reconnectPollInterval.toMillis())
        }
        if (finished.compareAndSet(false, true)) {
            sendReconnectTimeout(emitter, request)
        }
    }

    private fun sendReconnectTimeout(emitter: SseEmitter, request: AgentRunReconnectRequest) {
        try {
            sseEventWriter.send(
                emitter,
                AguiEvent.RunError(
                    request.threadId,
                    request.runId,
                    "reconnect_timeout",
                    "reconnect_timeout",
                ),
            )
            emitter.complete()
        } catch (ex: Exception) {
            emitter.completeWithError(ex)
        }
    }

    private fun reconnectInput(request: AgentRunReconnectRequest): RunAgentInput {
        return RunAgentInput.builder()
            .threadId(request.threadId)
            .runId(request.runId)
            .build()
    }
}
