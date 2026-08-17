/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.hitl.AgentInterruptStateRepository
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.runtime.AgentRunReplaySinkRegistry
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * run 事件流衔接：终态全量回放、active run 增量回放 + 本地 sink / 跨实例 Mongo 轮询。
 *
 * 不重新执行 Agent，也不重复归档 message（手册 §6.4）。
 */
@Component
class AgentRunStreamOrchestrator(
    private val properties: EffectiveAgentRuntimeProperties,
    private val agentRunRecordService: AgentRunRecordService,
    private val activeRunManager: ActiveRunManager,
    private val runEventService: AgentRunEventService,
    private val replaySinkRegistry: AgentRunReplaySinkRegistry,
    private val interruptStateRepository: AgentInterruptStateRepository,
    private val sseEventWriter: AguiSseEventWriter,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun stream(
        userId: String,
        projectId: String,
        threadId: String,
        runId: String?,
        lastEventIndex: Long?,
    ): SseEmitter {
        val runScope = ActiveRunScope(userId, projectId, threadId)
        val resolvedRunId = resolveRunId(runScope, threadId, runId)
        val existingRun = agentRunRecordService.findByRunId(resolvedRunId)
            ?: throw ParameterInvalidException("runId: run[$resolvedRunId] not found")
        if (existingRun.threadId != threadId) {
            throw ParameterInvalidException("threadId: run does not belong to thread[$threadId]")
        }
        val stillRunning = existingRun.status == AgentRunStatus.RUNNING || activeRunManager.isRunning(runScope)
        return if (stillRunning) {
            streamActiveRun(threadId, resolvedRunId, lastEventIndex, runScope)
        } else {
            replayTerminalRun(threadId, resolvedRunId, lastEventIndex ?: -1L, existingRun)
        }
    }

    /** 幂等 POST /run 时对已终态 run 重放事件，不重新执行 Agent。 */
    fun replayTerminalForRun(input: RunAgentInput, existingRun: TAgentRun): SseEmitter {
        return replayTerminalRun(input.threadId, input.runId, -1L, existingRun)
    }

    private fun resolveRunId(scope: ActiveRunScope, threadId: String, runId: String?): String {
        if (!runId.isNullOrBlank()) {
            return runId
        }
        activeRunManager.getActiveRunId(scope)?.let { return it }
        return agentRunRecordService.findLatestByThreadId(threadId)?.runId
            ?: throw ParameterInvalidException("runId: no run found for thread[$threadId]")
    }

    private fun replayTerminalRun(
        threadId: String,
        runId: String,
        afterIndex: Long,
        existingRun: TAgentRun,
    ): SseEmitter {
        logger.info("replay terminal run[{}] status[{}]", runId, existingRun.status)
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        Schedulers.boundedElastic().schedule {
            try {
                if (runEventService.hasEvents(runId)) {
                    runEventService.replayTo(emitter, runId, afterIndex)
                } else {
                    replaySyntheticTerminalEvents(emitter, threadId, runId, existingRun)
                }
                emitter.complete()
            } catch (ex: Exception) {
                emitter.completeWithError(ex)
            }
        }
        return emitter
    }

    private fun streamActiveRun(
        threadId: String,
        runId: String,
        lastEventIndex: Long?,
        runScope: ActiveRunScope,
    ): SseEmitter {
        logger.info("live stream run[{}] thread[{}]", runId, threadId)
        val emitter = SseEmitter(properties.reconnectTimeout.toMillis())
        val afterIndex = lastEventIndex ?: -1L
        val finished = AtomicBoolean(false)

        Schedulers.boundedElastic().schedule {
            try {
                var cursor = runEventService.replayTo(emitter, runId, afterIndex)
                if (attachLocalSink(runId, threadId, cursor, emitter, finished)) {
                    return@schedule
                }
                pollRemoteEvents(runId, threadId, runScope, cursor, emitter, finished)
            } catch (ex: Exception) {
                if (finished.compareAndSet(false, true)) {
                    emitter.completeWithError(ex)
                }
            }
        }
        return emitter
    }

    private fun attachLocalSink(
        runId: String,
        threadId: String,
        cursor: Long,
        emitter: SseEmitter,
        finished: AtomicBoolean,
    ): Boolean {
        val unsubscribe = replaySinkRegistry.subscribe(runId, cursor) { event ->
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
                sendStreamTimeout(emitter, threadId, runId)
            }
        }
        emitter.onError { unsubscribe() }
        return true
    }

    private fun pollRemoteEvents(
        runId: String,
        threadId: String,
        runScope: ActiveRunScope,
        startCursor: Long,
        emitter: SseEmitter,
        finished: AtomicBoolean,
    ) {
        var cursor = startCursor
        val deadline = System.currentTimeMillis() + properties.reconnectTimeout.toMillis()
        while (!finished.get() && System.currentTimeMillis() < deadline) {
            val events = runEventService.listAfterIndex(runId, cursor)
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
            val latestRun = agentRunRecordService.findByRunId(runId)
            if (latestRun?.status != AgentRunStatus.RUNNING && !activeRunManager.isRunning(runScope)) {
                finished.set(true)
                emitter.complete()
                return
            }
            Thread.sleep(properties.reconnectPollInterval.toMillis())
        }
        if (finished.compareAndSet(false, true)) {
            sendStreamTimeout(emitter, threadId, runId)
        }
    }

    private fun sendStreamTimeout(emitter: SseEmitter, threadId: String, runId: String) {
        try {
            sseEventWriter.send(
                emitter,
                AguiEvent.RunError(
                    threadId,
                    runId,
                    "stream_timeout",
                    "stream_timeout",
                ),
            )
            emitter.complete()
        } catch (ex: Exception) {
            emitter.completeWithError(ex)
        }
    }

    /**
     * 事件库为空时的最小终态回放（迁移期兼容：Phase C 之前完成的 run 无持久化事件）。
     */
    private fun replaySyntheticTerminalEvents(
        emitter: SseEmitter,
        threadId: String,
        runId: String,
        existingRun: TAgentRun,
    ) {
        sseEventWriter.send(emitter, AguiEvent.RunStarted(threadId, runId, null, null))
        when (existingRun.status) {
            AgentRunStatus.FAILED -> sseEventWriter.send(
                emitter,
                AguiEvent.RunError(
                    threadId,
                    runId,
                    existingRun.errorCode ?: "run_failed",
                    existingRun.errorCode,
                ),
            )
            AgentRunStatus.SUSPENDED -> sseEventWriter.send(
                emitter,
                AguiEvent.RunFinished(
                    threadId,
                    runId,
                    null,
                    AguiEvent.RunFinishedInterruptOutcome(pendingInterrupts(threadId)),
                ),
            )
            else -> sseEventWriter.send(
                emitter,
                AguiEvent.RunFinished(
                    threadId,
                    runId,
                    null,
                    AguiEvent.RunFinishedSuccessOutcome(),
                ),
            )
        }
    }

    private fun pendingInterrupts(threadId: String): List<AguiEvent.Interrupt> {
        val snapshots = interruptStateRepository.getPendingInterrupt(threadId)?.interrupts ?: return emptyList()
        return snapshots.map { snapshot ->
            AguiEvent.Interrupt(
                snapshot.id,
                snapshot.reason,
                snapshot.message,
                snapshot.toolCallId,
                snapshot.responseSchema,
                snapshot.expiresAt,
                snapshot.metadata,
            )
        }
    }
}
