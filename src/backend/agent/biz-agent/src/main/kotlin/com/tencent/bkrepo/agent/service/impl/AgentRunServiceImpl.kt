/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.agui.AgentForwardedPropsSupport
import com.tencent.bkrepo.agent.agui.AguiInterruptTracker
import com.tencent.bkrepo.agent.agui.AguiMessageArchiveHandler
import com.tencent.bkrepo.agent.agui.AguiResumeValidator
import com.tencent.bkrepo.agent.agui.BkrepoAguiRuntimeContextResolver
import com.tencent.bkrepo.agent.agui.FrontendToolSanitizer
import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.constant.AGENT_RUN_ID_PREFIX
import com.tencent.bkrepo.agent.constant.AGENT_THREAD_ID_PREFIX
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunTriggerType
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatusInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStopRequest
import com.tencent.bkrepo.agent.runtime.AgentRunHandleRegistry
import com.tencent.bkrepo.agent.runtime.AgentSessionInterruptor
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.agent.service.AgentRunService
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.session.AgentActiveRunStore
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import com.tencent.bkrepo.agent.session.AgentRunCancelStore
import com.tencent.bkrepo.agent.session.AgentRunLock
import com.tencent.bkrepo.agent.session.AgentSessionStore
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import io.agentscope.core.agui.encoder.AguiEventEncoder
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import io.agentscope.core.agui.processor.AguiRequestProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.Disposable
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Agent 对话入口：权限校验、会话/run 编排、AG-UI SSE 事件输出与消息归档。
 */
@Service
class AgentRunServiceImpl(
    private val properties: AgentProperties,
    private val permissionManager: PermissionManager,
    private val agentSessionStore: AgentSessionStore,
    private val agentSessionService: AgentSessionService,
    private val agentRunLock: AgentRunLock,
    private val agentSessionInterruptor: AgentSessionInterruptor,
    private val agentRunRecordService: AgentRunRecordService,
    private val aguiRequestProcessor: AguiRequestProcessor,
    private val aguiResumeValidator: AguiResumeValidator,
    private val frontendToolSanitizer: FrontendToolSanitizer,
    private val aguiInterruptTracker: AguiInterruptTracker,
    private val pendingInterruptStore: AgentPendingInterruptStore,
    private val activeRunStore: AgentActiveRunStore,
    private val runCancelStore: AgentRunCancelStore,
    private val runHandleRegistry: AgentRunHandleRegistry,
    private val aguiRuntimeContextResolver: BkrepoAguiRuntimeContextResolver,
    private val aguiMessageArchiveHandler: AguiMessageArchiveHandler,
) : AgentRunService {

    private val aguiEventEncoder = AguiEventEncoder()

    override fun createSession(userId: String, projectId: String): AgentSessionCreateResult {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        val threadId = AGENT_THREAD_ID_PREFIX + StringPool.uniqueId()
        agentSessionStore.bindSession(userId, projectId, threadId)
        return try {
            agentSessionService.createSessionRecord(threadId, userId, projectId)
        } catch (ex: Exception) {
            agentSessionStore.removeSession(projectId, threadId)
            throw ex
        }
    }

    /**
     * 发起一轮 AG-UI run，以 SSE 推送 [AguiEvent]。
     *
     * canonical runId 来自 [RunAgentInput.runId]；服务端另建 executionId 供内部 run 记录追踪。
     */
    override fun run(
        userId: String,
        projectId: String,
        input: RunAgentInput,
    ): SseEmitter {
        validate(input)
        aguiResumeValidator.validateAndPrepare(userId, projectId, input)
        val processedInput = frontendToolSanitizer.sanitize(input)
        val threadId = processedInput.threadId
        val runId = processedInput.runId
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, threadId)

        val existingRun = agentRunRecordService.findByRunId(runId)
        if (existingRun != null) {
            return when (existingRun.status) {
                AgentRunStatus.RUNNING -> throw TooManyRequestsException("Run[$runId] is already running")
                else -> replayTerminalRun(processedInput, existingRun)
            }
        }

        if (!agentRunLock.tryAcquire(userId, threadId)) {
            throw TooManyRequestsException("Session[$threadId] is already running")
        }
        val transport = AgentForwardedPropsSupport.extract(processedInput)
        val executionId = AGENT_RUN_ID_PREFIX + StringPool.uniqueId()
        val triggerType = if (processedInput.hasResume()) {
            AgentRunTriggerType.AGUI_RESUME
        } else {
            AgentRunTriggerType.USER_INPUT
        }
        agentRunRecordService.startRun(
            runId = runId,
            executionId = executionId,
            threadId = threadId,
            userId = userId,
            projectId = projectId,
            deviceId = transport.deviceId,
            entryAgentId = properties.name,
            triggerType = triggerType,
        )
        agentSessionService.touchSession(threadId, runId)
        agentSessionStore.touchSessionOwner(userId, projectId, threadId)
        activeRunStore.bind(userId, threadId, runId)
        runCancelStore.clear(runId)

        val archiveState = AguiMessageArchiveHandler.State()
        aguiMessageArchiveHandler.archiveIncomingUserMessages(processedInput, threadId, runId)

        val interruptState = AguiInterruptTracker.State()

        val runtimeContext = aguiRuntimeContextResolver.resolve(
            userId = userId,
            projectId = projectId,
            input = processedInput,
            deviceId = transport.deviceId,
            executionId = executionId,
            traceId = transport.traceId,
        )
        val processResult = aguiRequestProcessor.process(processedInput, null, null, runtimeContext)
        val terminalStatus = AtomicReference(AgentRunStatus.COMPLETED)
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        val subscriptionRef = AtomicReference<Disposable>()
        val sseFinished = AtomicBoolean(false)

        fun releaseRunLock() {
            agentRunLock.release(userId, threadId)
        }

        fun abortInflightAgentRun() {
            agentSessionInterruptor.interrupt(runtimeContext)
        }

        fun cleanupRunResources() {
            releaseRunLock()
            activeRunStore.clear(userId, threadId)
            runCancelStore.clear(runId)
            runHandleRegistry.remove(userId, threadId)
        }

        fun finishSse(
            disposeSubscription: Boolean = true,
            abortAgent: Boolean = false,
            runStatus: AgentRunStatus = terminalStatus.get(),
            cancelReason: String? = null,
            errorCode: String? = null,
        ) {
            if (!sseFinished.compareAndSet(false, true)) return
            if (abortAgent) abortInflightAgentRun()
            if (disposeSubscription) subscriptionRef.get()?.dispose()
            agentRunRecordService.finishRun(
                runId = runId,
                status = runStatus,
                cancelReason = cancelReason,
                errorCode = errorCode,
            )
            cleanupRunResources()
            emitter.complete()
        }

        runHandleRegistry.register(
            AgentRunHandleRegistry.Handle(
                userId = userId,
                threadId = threadId,
                runId = runId,
                runtimeContext = runtimeContext,
                abort = {
                    finishSse(
                        abortAgent = true,
                        runStatus = AgentRunStatus.CANCELLED,
                        cancelReason = "user_stop",
                    )
                },
            ),
        )

        val subscription = processResult.events()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { event ->
                    try {
                        if (runCancelStore.isCancelled(runId)) {
                            aguiMessageArchiveHandler.forceFinalizeAssistant(threadId, runId, archiveState)
                            finishSse(
                                abortAgent = true,
                                runStatus = AgentRunStatus.CANCELLED,
                                cancelReason = "user_stop",
                            )
                            return@subscribe
                        }
                        aguiInterruptTracker.onEvent(event, interruptState)
                        trackTerminalStatus(event, terminalStatus)
                        handlePendingInterrupt(threadId, runId, event, terminalStatus.get(), interruptState)
                        aguiMessageArchiveHandler.onEvent(event, threadId, runId, archiveState)
                        emitter.send(
                            SseEmitter.event()
                                .data(aguiEventEncoder.encodeToJson(event), MediaType.APPLICATION_JSON),
                        )
                    } catch (ignored: IOException) {
                        logger.info("agent ag-ui sse closed by client, session[$threadId]")
                        aguiMessageArchiveHandler.forceFinalizeAssistant(threadId, runId, archiveState)
                        finishSse(
                            abortAgent = true,
                            runStatus = AgentRunStatus.CANCELLED,
                            cancelReason = "client_disconnected",
                        )
                    }
                },
                { error ->
                    logger.error("agent ag-ui run failed, user[$userId] session[$threadId]", error)
                    if (sseFinished.compareAndSet(false, true)) {
                        abortInflightAgentRun()
                        subscriptionRef.get()?.dispose()
                        agentRunRecordService.finishRun(
                            runId = runId,
                            status = AgentRunStatus.FAILED,
                            errorCode = error.javaClass.simpleName,
                        )
                        cleanupRunResources()
                        emitter.completeWithError(error)
                    }
                },
                { finishSse() },
            )
        subscriptionRef.set(subscription)
        emitter.onCompletion {
            if (sseFinished.compareAndSet(false, true)) {
                abortInflightAgentRun()
                subscription.dispose()
                cleanupRunResources()
            }
        }
        emitter.onError {
            if (sseFinished.compareAndSet(false, true)) {
                abortInflightAgentRun()
                subscription.dispose()
                cleanupRunResources()
            }
        }
        emitter.onTimeout {
            logger.info("agent ag-ui run timeout, user[$userId] session[$threadId]")
            if (sseFinished.compareAndSet(false, true)) {
                abortInflightAgentRun()
                subscription.dispose()
                agentRunRecordService.finishRun(
                    runId = runId,
                    status = AgentRunStatus.CANCELLED,
                    cancelReason = "timeout",
                )
                cleanupRunResources()
                emitter.complete()
            }
        }
        return emitter
    }

    override fun listSessions(userId: String, projectId: String, pageNumber: Int, pageSize: Int): Page<AgentSessionInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        return agentSessionService.listSessions(userId, projectId, pageNumber, pageSize)
    }

    override fun listMessages(
        userId: String,
        projectId: String,
        threadId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        return agentSessionService.listMessages(userId, projectId, threadId, pageNumber, pageSize)
    }

    override fun updateSessionTitle(userId: String, projectId: String, request: AgentSessionUpdateRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.updateTitle(userId, projectId, request.threadId, request.title)
    }

    override fun deleteSession(userId: String, projectId: String, request: AgentSessionDeleteRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        pendingInterruptStore.clear(request.threadId)
        activeRunStore.clear(userId, request.threadId)
        agentSessionService.deleteSession(userId, projectId, request.threadId)
    }

    override fun getRunStatus(userId: String, projectId: String, threadId: String): AgentRunStatusInfo {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, threadId)
        val running = agentRunLock.isRunning(userId, threadId)
        val activeRunId = activeRunStore.get(userId, threadId)
        val latestRun = agentRunRecordService.findLatestByThreadId(threadId)
        val runId = activeRunId ?: latestRun?.runId
        val status = when {
            running -> AgentRunStatus.RUNNING
            latestRun != null -> latestRun.status
            else -> null
        }
        return AgentRunStatusInfo(
            threadId = threadId,
            runId = runId,
            status = status,
            running = running,
            hasPendingInterrupt = pendingInterruptStore.get(threadId) != null,
        )
    }

    override fun stopRun(userId: String, projectId: String, request: AgentRunStopRequest): Boolean {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.threadId)
        val activeRunId = activeRunStore.get(userId, request.threadId)
            ?: agentRunRecordService.findLatestByThreadId(request.threadId)
                ?.takeIf { it.status == AgentRunStatus.RUNNING }
                ?.runId
        if (activeRunId == null) {
            return false
        }
        if (request.runId != null && request.runId != activeRunId) {
            throw ParameterInvalidException("runId: run[${request.runId}] is not active")
        }
        runCancelStore.requestCancel(activeRunId)
        runHandleRegistry.find(userId, request.threadId)
            ?.takeIf { it.runId == activeRunId }
            ?.abort()
        return true
    }

    override fun reconnectRun(
        userId: String,
        projectId: String,
        request: AgentRunReconnectRequest,
    ): SseEmitter {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.threadId)
        Preconditions.checkNotBlank(request.runId, "runId")
        val existingRun = agentRunRecordService.findByRunId(request.runId)
            ?: throw ParameterInvalidException("runId: run[${request.runId}] not found")
        if (existingRun.threadId != request.threadId) {
            throw ParameterInvalidException("threadId: run does not belong to thread[${request.threadId}]")
        }
        if (existingRun.status == AgentRunStatus.RUNNING) {
            throw TooManyRequestsException("Run[${request.runId}] is still running")
        }
        val input = RunAgentInput.builder()
            .threadId(request.threadId)
            .runId(request.runId)
            .build()
        return replayTerminalRun(input, existingRun)
    }

    /**
     * 对已终态的 run 重放最小 AG-UI 事件序列，避免重复执行 agent。
     */
    private fun replayTerminalRun(input: RunAgentInput, existingRun: TAgentRun): SseEmitter {
        logger.info("replay terminal run[${existingRun.runId}] status[${existingRun.status}]")
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        Schedulers.boundedElastic().schedule {
            try {
                sendEncoded(emitter, AguiEvent.RunStarted(input.threadId, input.runId, null, input))
                when (existingRun.status) {
                    AgentRunStatus.FAILED -> sendEncoded(
                        emitter,
                        AguiEvent.RunError(
                            input.threadId,
                            input.runId,
                            existingRun.errorCode ?: "run_failed",
                            existingRun.errorCode,
                        ),
                    )
                    AgentRunStatus.SUSPENDED -> sendEncoded(
                        emitter,
                        AguiEvent.RunFinished(
                            input.threadId,
                            input.runId,
                            null,
                            AguiEvent.RunFinishedInterruptOutcome(pendingInterrupts(input.threadId)),
                        ),
                    )
                    else -> sendEncoded(
                        emitter,
                        AguiEvent.RunFinished(
                            input.threadId,
                            input.runId,
                            null,
                            AguiEvent.RunFinishedSuccessOutcome(),
                        ),
                    )
                }
                emitter.complete()
            } catch (ex: Exception) {
                emitter.completeWithError(ex)
            }
        }
        return emitter
    }

    /**
     * 从 [AgentPendingInterruptStore] 还原完整的 AG-UI [AguiEvent.Interrupt] 列表，
     * 供 reconnect 时重放，使前端能重新渲染审批 UI（而不是空的 interrupt 事件）。
     */
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

    private fun sendEncoded(emitter: SseEmitter, event: AguiEvent) {
        emitter.send(
            SseEmitter.event()
                .data(aguiEventEncoder.encodeToJson(event), MediaType.APPLICATION_JSON),
        )
    }

    private fun validate(input: RunAgentInput) {
        Preconditions.checkNotBlank(input.threadId, "threadId")
        Preconditions.checkArgument(input.threadId.length <= properties.maxThreadIdLength, "threadId")
        Preconditions.checkNotBlank(input.runId, "runId")
        Preconditions.checkArgument(input.hasMessages() || input.hasResume(), "messages or resume")
        if (input.hasMessages()) {
            val latestUserText = input.messages
                .asReversed()
                .firstOrNull { "user".equals(it.role, ignoreCase = true) }
                ?.textContent
                ?: ""
            if (latestUserText.isNotBlank()) {
                Preconditions.checkArgument(latestUserText.length <= properties.maxMessageLength, "messages")
            }
        }
    }

    private fun handlePendingInterrupt(
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

    private fun trackTerminalStatus(event: AguiEvent, terminalStatus: AtomicReference<AgentRunStatus>) {
        when (event) {
            is AguiEvent.RunFinished -> {
                val outcome = event.outcome()
                terminalStatus.set(
                    if (outcome is AguiEvent.RunFinishedInterruptOutcome) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(AgentRunServiceImpl::class.java)
    }
}
