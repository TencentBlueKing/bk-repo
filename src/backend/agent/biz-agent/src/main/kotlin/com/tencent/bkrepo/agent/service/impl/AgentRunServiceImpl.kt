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

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.constant.AGENT_RUN_ID_PREFIX
import com.tencent.bkrepo.agent.constant.AGENT_SESSION_ID_PREFIX
import com.tencent.bkrepo.agent.identity.RuntimeContextFactory
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentRunRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.service.AgentMessageArchiveService
import com.tencent.bkrepo.agent.service.AgentRunService
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.session.AgentRunLock
import com.tencent.bkrepo.agent.session.AgentSessionStore
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import io.agentscope.core.event.AgentEvent
import io.agentscope.core.event.AgentResultEvent
import io.agentscope.core.event.RequireExternalExecutionEvent
import io.agentscope.core.event.TextBlockDeltaEvent
import io.agentscope.core.message.GenerateReason
import io.agentscope.core.message.Msg
import io.agentscope.core.message.ToolResultBlock
import io.agentscope.core.message.ToolResultMessage
import io.agentscope.core.message.ToolUseBlock
import io.agentscope.core.message.UserMessage
import io.agentscope.harness.agent.HarnessAgent
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.Disposable
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
class AgentRunServiceImpl(
    private val agent: HarnessAgent,
    private val properties: AgentProperties,
    private val permissionManager: PermissionManager,
    private val agentSessionStore: AgentSessionStore,
    private val agentSessionService: AgentSessionService,
    private val agentMessageArchiveService: AgentMessageArchiveService,
    private val agentRunLock: AgentRunLock,
    private val runtimeContextFactory: RuntimeContextFactory,
) : AgentRunService {

    override fun createSession(userId: String, projectId: String, deviceId: String?): AgentSessionInfo {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        val sessionId = AGENT_SESSION_ID_PREFIX + StringPool.uniqueId()
        agentSessionStore.bindSession(userId, projectId, sessionId)
        return agentSessionService.createSessionRecord(sessionId, userId, projectId, deviceId)
    }

    override fun run(userId: String, projectId: String, deviceId: String?, request: AgentRunRequest): SseEmitter {
        validate(request)
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.sessionId)
        if (!agentRunLock.tryAcquire(userId, request.sessionId)) {
            throw TooManyRequestsException("Session[${request.sessionId}] is already running")
        }
        val runId = AGENT_RUN_ID_PREFIX + StringPool.uniqueId()
        agentSessionService.touchSession(request.sessionId, runId)
        if (request.content.isNotBlank()) {
            agentMessageArchiveService.archiveUserMessage(request.sessionId, runId, request.content)
        }
        val runtimeContext = runtimeContextFactory.create(userId, projectId, request.sessionId, deviceId)
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        val subscriptionRef = AtomicReference<Disposable>()
        val pendingExternalExecution = AtomicReference(false)
        val sseFinished = AtomicBoolean(false)
        val assistantText = StringBuilder()
        fun releaseRunLock() {
            agentRunLock.release(userId, request.sessionId)
        }
        fun finishSse(disposeSubscription: Boolean = true, archiveAssistant: Boolean = true) {
            if (!sseFinished.compareAndSet(false, true)) return
            if (disposeSubscription) subscriptionRef.get()?.dispose()
            releaseRunLock()
            if (archiveAssistant && assistantText.isNotEmpty()) {
                agentMessageArchiveService.archiveAssistantMessage(
                    sessionId = request.sessionId,
                    runId = runId,
                    content = assistantText.toString(),
                    agentId = properties.name,
                )
            }
            emitter.complete()
        }
        val subscription = agent
            .streamEvents(buildResumeMessage(userId, request), runtimeContext)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { event ->
                    try {
                        if (event is TextBlockDeltaEvent) {
                            assistantText.append(event.delta)
                        }
                        if (emitAgentEvent(emitter, event, pendingExternalExecution)) {
                            finishSse(archiveAssistant = false)
                        }
                    } catch (ignored: IOException) {
                        logger.info("agent sse closed by client, session[${request.sessionId}]")
                        finishSse()
                    }
                },
                { error ->
                    logger.error("agent run failed, user[$userId] session[${request.sessionId}]", error)
                    if (sseFinished.compareAndSet(false, true)) {
                        subscriptionRef.get()?.dispose()
                        releaseRunLock()
                        emitter.completeWithError(error)
                    }
                },
                { finishSse() },
            )
        subscriptionRef.set(subscription)
        emitter.onCompletion { subscription.dispose() }
        emitter.onError { subscription.dispose() }
        emitter.onTimeout {
            logger.info("agent run timeout, user[$userId] session[${request.sessionId}]")
            subscription.dispose()
            if (sseFinished.compareAndSet(false, true)) {
                releaseRunLock()
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
        sessionId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        return agentSessionService.listMessages(userId, projectId, sessionId, pageNumber, pageSize)
    }

    override fun updateSessionTitle(userId: String, projectId: String, request: AgentSessionUpdateRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.updateTitle(userId, projectId, request.sessionId, request.title)
    }

    override fun deleteSession(userId: String, projectId: String, request: AgentSessionDeleteRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.deleteSession(userId, projectId, request.sessionId)
    }

    private fun validate(request: AgentRunRequest) {
        Preconditions.checkNotBlank(request.sessionId, "sessionId")
        Preconditions.checkArgument(request.sessionId.length <= properties.maxSessionIdLength, "sessionId")
        val hasContent = request.content.isNotBlank()
        val hasExternal = !request.externalExecutionResults.isNullOrEmpty()
        Preconditions.checkArgument(hasContent || hasExternal, "content or externalExecutionResults")
        if (hasContent) {
            Preconditions.checkArgument(request.content.length <= properties.maxMessageLength, "content")
        }
    }

    /**
     * @return true 表示本轮 SSE 应结束，等待客户端回传 externalExecutionResults
     */
    private fun emitAgentEvent(
        emitter: SseEmitter,
        event: AgentEvent,
        pendingExternalExecution: AtomicReference<Boolean>,
    ): Boolean {
        if (event is AgentResultEvent) {
            val result = event.result
            if (result != null && result.generateReason == GenerateReason.TOOL_SUSPENDED) {
                val toolCalls = result.getContentBlocks(ToolUseBlock::class.java)
                if (toolCalls.isNotEmpty()) {
                    val externalEvent = RequireExternalExecutionEvent("", toolCalls)
                    pendingExternalExecution.set(true)
                    emitter.send(
                        SseEmitter.event()
                            .id(externalEvent.id)
                            .name(externalEvent.type.value)
                            .data(externalEvent, MediaType.APPLICATION_JSON),
                    )
                    return true
                }
            }
        }
        if (pendingExternalExecution.get() == true && event.type.value == "AGENT_END") {
            return false
        }
        emitter.send(
            SseEmitter.event()
                .id(event.id)
                .name(event.type.value)
                .data(event, MediaType.APPLICATION_JSON),
        )
        return false
    }

    private fun buildResumeMessage(userId: String, request: AgentRunRequest): Msg {
        val externalResults = request.externalExecutionResults
        if (!externalResults.isNullOrEmpty()) {
            val blocks = externalResults.map { dto ->
                ToolResultBlock.text(dto.payload)
                    .withIdAndName(dto.callId, dto.toolName)
            }
            return ToolResultMessage.builder()
                .results(blocks)
                .build()
        }
        return UserMessage(userId, request.content)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentRunServiceImpl::class.java)
    }
}
