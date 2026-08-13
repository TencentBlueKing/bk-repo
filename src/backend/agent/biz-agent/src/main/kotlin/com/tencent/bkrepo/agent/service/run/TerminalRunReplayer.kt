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
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.scheduler.Schedulers

/** 对已终态 run 重放最小 AG-UI 事件序列，避免重复执行 agent。 */
@Component
class TerminalRunReplayer(
    private val properties: EffectiveAgentRuntimeProperties,
    private val pendingInterruptStore: AgentPendingInterruptStore,
    private val runEventService: AgentRunEventService,
    private val sseEventWriter: AguiSseEventWriter,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun replay(input: RunAgentInput, existingRun: TAgentRun): SseEmitter {
        logger.info("replay terminal run[{}] status[{}]", existingRun.runId, existingRun.status)
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        Schedulers.boundedElastic().schedule {
            try {
                if (runEventService.hasEvents(existingRun.runId)) {
                    runEventService.replayTo(emitter, existingRun.runId)
                } else {
                    replaySyntheticEvents(emitter, input, existingRun)
                }
                emitter.complete()
            } catch (ex: Exception) {
                emitter.completeWithError(ex)
            }
        }
        return emitter
    }

    private fun replaySyntheticEvents(emitter: SseEmitter, input: RunAgentInput, existingRun: TAgentRun) {
        sseEventWriter.send(emitter, AguiEvent.RunStarted(input.threadId, input.runId, null, input))
        when (existingRun.status) {
            AgentRunStatus.FAILED -> sseEventWriter.send(
                emitter,
                AguiEvent.RunError(
                    input.threadId,
                    input.runId,
                    existingRun.errorCode ?: "run_failed",
                    existingRun.errorCode,
                ),
            )
            AgentRunStatus.SUSPENDED -> sseEventWriter.send(
                emitter,
                AguiEvent.RunFinished(
                    input.threadId,
                    input.runId,
                    null,
                    AguiEvent.RunFinishedInterruptOutcome(pendingInterrupts(input.threadId)),
                ),
            )
            else -> sseEventWriter.send(
                emitter,
                AguiEvent.RunFinished(
                    input.threadId,
                    input.runId,
                    null,
                    AguiEvent.RunFinishedSuccessOutcome(),
                ),
            )
        }
    }

    private fun pendingInterrupts(threadId: String): List<AguiEvent.Interrupt> {
        val snapshots = pendingInterruptStore.get(threadId)?.interrupts ?: return emptyList()
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
