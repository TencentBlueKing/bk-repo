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
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_FORCE_ARCHIVE_ASSISTANT
import com.tencent.bkrepo.agent.identity.RuntimeContextFactory
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentRunRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.runtime.AgentSessionInterruptor
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

/**
 * Agent 对话入口：权限校验、会话/run 编排、SSE 事件转发与消息归档。
 */
@Service
class AgentRunServiceImpl(
    private val agent: HarnessAgent,
    private val properties: AgentProperties,
    private val permissionManager: PermissionManager,
    private val agentSessionStore: AgentSessionStore,
    private val agentSessionService: AgentSessionService,
    private val agentRunLock: AgentRunLock,
    private val agentSessionInterruptor: AgentSessionInterruptor,
    private val runtimeContextFactory: RuntimeContextFactory,
) : AgentRunService {

    /**
     * 创建新会话：生成 sessionId，写入 Redis 归属与 Mongo 元数据。
     */
    override fun createSession(userId: String, projectId: String): AgentSessionCreateResult {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        val sessionId = AGENT_SESSION_ID_PREFIX + StringPool.uniqueId()
        agentSessionStore.bindSession(userId, projectId, sessionId)
        return try {
            agentSessionService.createSessionRecord(sessionId, userId, projectId)
        } catch (ex: Exception) {
            agentSessionStore.removeSession(projectId, sessionId)
            throw ex
        }
    }

    /**
     * 发起一轮对话，以 SSE 推送 AgentScope 事件流。
     *
     * 一次 HTTP 请求对应一个前台 [runId]；客户端通过多次 POST 完成「用户输入 → 模型推理 →
     * 本地工具挂起 → 回传 externalExecutionResults → 续跑」的完整回合。
     *
     * 生命周期要点：
     * - 入口校验权限与会话归属，并持有 run 锁直至 SSE 结束；
     * - USER/ASSISTANT 消息由 [com.tencent.bkrepo.agent.middleware.MessageArchiveMiddleware] 归档；
     * - 本地工具挂起（REQUIRE_EXTERNAL_EXECUTION）时提前关闭 SSE 并释放锁，不归档半成品 assistant；
     * - 续跑请求只带 [AgentRunRequest.externalExecutionResults]、content 可为空，不再重复归档 USER。
     */
    override fun run(userId: String, projectId: String, deviceId: String?, request: AgentRunRequest): SseEmitter {
        validate(request)
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.sessionId)
        // 同会话前台 run 互斥；获取失败立即拒绝，不排队。续跑也视为新的 HTTP run，需重新抢锁。
        if (!agentRunLock.tryAcquire(userId, request.sessionId)) {
            throw TooManyRequestsException("Session[${request.sessionId}] is already running")
        }
        val runId = AGENT_RUN_ID_PREFIX + StringPool.uniqueId()
        agentSessionService.touchSession(request.sessionId, runId)
        agentSessionStore.touchSessionOwner(userId, projectId, request.sessionId)
        // RuntimeContext 冻结 userId/projectId/sessionId/deviceId/runId，供工具、权限与归档 middleware 读取。
        val runtimeContext = runtimeContextFactory.create(
            userId = userId,
            projectId = projectId,
            sessionId = request.sessionId,
            deviceId = deviceId,
            runId = runId,
        )
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        val subscriptionRef = AtomicReference<Disposable>()
        val pendingExternalExecution = AtomicReference(false)
        // 防止 onComplete / onError / onTimeout / finishSse 重复释放锁或 complete emitter。
        val sseFinished = AtomicBoolean(false)
        fun releaseRunLock() {
            agentRunLock.release(userId, request.sessionId)
        }
        fun abortInflightAgentRun() {
            agentSessionInterruptor.interrupt(runtimeContext)
        }
        /**
         * 结束本轮 SSE。默认释放 run 锁、dispose 订阅。
         *
         * @param abortAgent 客户端断连/超时/异常时为 true，通知 AgentScope 中断推理。
         */
        fun finishSse(disposeSubscription: Boolean = true, abortAgent: Boolean = false) {
            if (!sseFinished.compareAndSet(false, true)) return
            if (abortAgent) abortInflightAgentRun()
            if (disposeSubscription) subscriptionRef.get()?.dispose()
            releaseRunLock()
            emitter.complete()
        }
        // HarnessAgent 无请求级状态，会话恢复依赖 AgentStateStore + 本次 runtimeContext。
        val subscription = agent
            .streamEvents(buildResumeMessage(userId, request), runtimeContext)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { event ->
                    try {
                        if (emitAgentEvent(emitter, event, pendingExternalExecution)) {
                            // TOOL_SUSPENDED：通知客户端执行本地工具，本连接到此结束。
                            finishSse()
                        }
                    } catch (ignored: IOException) {
                        // 客户端断开连接；通知 middleware 在 cancel 时归档已有 assistant 片段。
                        logger.info("agent sse closed by client, session[${request.sessionId}]")
                        runtimeContext.put(RUNTIME_CONTEXT_FORCE_ARCHIVE_ASSISTANT, true)
                        finishSse(abortAgent = true)
                    }
                },
                { error ->
                    logger.error("agent run failed, user[$userId] session[${request.sessionId}]", error)
                    if (sseFinished.compareAndSet(false, true)) {
                        abortInflightAgentRun()
                        subscriptionRef.get()?.dispose()
                        releaseRunLock()
                        // 异常路径不归档 assistant，避免历史库留下截断回复。
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
                releaseRunLock()
            }
        }
        emitter.onError {
            if (sseFinished.compareAndSet(false, true)) {
                abortInflightAgentRun()
                subscription.dispose()
                releaseRunLock()
            }
        }
        emitter.onTimeout {
            logger.info("agent run timeout, user[$userId] session[${request.sessionId}]")
            if (sseFinished.compareAndSet(false, true)) {
                abortInflightAgentRun()
                subscription.dispose()
                releaseRunLock()
                // 超时与异常类似，不保证 assistant 完整，故不归档。
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

    /**
     * run 请求体校验。
     *
     * 两种合法入口：带 [AgentRunRequest.content] 的新一轮用户输入，或仅带
     * [AgentRunRequest.externalExecutionResults] 的本地工具续跑（content 可空）。
     */
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
     * 将 AgentScope 内部事件透传为 SSE 帧。
     *
     * @return `true` 表示本轮 SSE 应结束（已下发 REQUIRE_EXTERNAL_EXECUTION，等待客户端本地执行）
     */
    private fun emitAgentEvent(
        emitter: SseEmitter,
        event: AgentEvent,
        pendingExternalExecution: AtomicReference<Boolean>,
    ): Boolean {
        if (event is AgentResultEvent) {
            val result = event.result
            // 模型因外部本地工具而挂起：不下发完整 AgentResult，改为 REQUIRE_EXTERNAL_EXECUTION 协议事件。
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
        // 挂起后 HarnessAgent 仍可能发出 AGENT_END；此时连接已在上面关闭，忽略以免重复发送。
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

    /**
     * 构造本轮送入 HarnessAgent 的消息。
     *
     * - 有 externalExecutionResults：ToolResultMessage，恢复 pending tool；
     * - 否则：UserMessage，开启新一轮用户输入。
     */
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
