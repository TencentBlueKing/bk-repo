/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.runtime.AgentSessionInterruptor
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** run 结束、资源清理、跨副本 cancel 与 SSE 收尾。 */
@Component
class AgentRunLifecycleManager(
    private val activeRunManager: ActiveRunManager,
    private val agentSessionInterruptor: AgentSessionInterruptor,
    private val agentRunRecordService: AgentRunRecordService,
    private val runEventService: AgentRunEventService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    data class FinishOptions(
        val disposeSubscription: Boolean = true,
        val abortAgent: Boolean = false,
        val runStatus: AgentRunStatus? = null,
        val cancelReason: String? = null,
        val errorCode: String? = null,
    )

    fun registerHandle(scope: AgentRunScope) {
        activeRunManager.registerHandle(
            scope = scope.activeRunScope(),
            runId = scope.runId,
            runtimeContext = scope.runtimeContext,
            abort = {
                finish(
                    scope,
                    FinishOptions(
                        abortAgent = true,
                        runStatus = AgentRunStatus.CANCELLED,
                        cancelReason = "user_stop",
                    ),
                )
            },
        )
    }

    fun attachEmitterCallbacks(scope: AgentRunScope) {
        scope.emitter.onCompletion { markPrimaryClientDisconnected(scope) }
        scope.emitter.onError { markPrimaryClientDisconnected(scope) }
        scope.emitter.onTimeout { markPrimaryClientDisconnected(scope) }
    }

    fun markPrimaryClientDisconnected(scope: AgentRunScope) {
        if (scope.primaryClientDisconnected.compareAndSet(false, true)) {
            logger.info("primary sse disconnected, run continues: threadId={}", scope.threadId)
        }
    }

    fun finish(scope: AgentRunScope, options: FinishOptions = FinishOptions()) {
        if (!scope.runFinished.compareAndSet(false, true)) return
        if (options.abortAgent) {
            agentSessionInterruptor.interrupt(scope.runtimeContext)
        }
        if (options.disposeSubscription) {
            scope.subscriptionRef.get()?.dispose()
        }
        agentRunRecordService.finishRun(
            runId = scope.runId,
            status = options.runStatus ?: scope.terminalStatus.get(),
            cancelReason = options.cancelReason,
            errorCode = options.errorCode,
        )
        cleanup(scope)
        completeEmitter(scope.emitter)
    }

    fun finishWithError(scope: AgentRunScope, error: Throwable) {
        if (!scope.runFinished.compareAndSet(false, true)) return
        agentSessionInterruptor.interrupt(scope.runtimeContext)
        scope.subscriptionRef.get()?.dispose()
        agentRunRecordService.finishRun(
            runId = scope.runId,
            status = AgentRunStatus.FAILED,
            errorCode = error.javaClass.simpleName,
        )
        cleanup(scope)
        completeEmitter(scope.emitter)
    }

    private fun cleanup(scope: AgentRunScope) {
        runEventService.closeRun(scope.runId)
        activeRunManager.removeHandle(scope.activeRunScope())
        activeRunManager.releaseRun(scope.activeRunScope(), scope.runId)
    }

    private fun completeEmitter(emitter: SseEmitter) {
        try {
            emitter.complete()
        } catch (ex: Exception) {
            logger.debug("SSE complete ignored: {}", ex.message)
        }
    }
}

fun AgentRunScope.activeRunScope(): ActiveRunScope = ActiveRunScope(userId, projectId, threadId)
