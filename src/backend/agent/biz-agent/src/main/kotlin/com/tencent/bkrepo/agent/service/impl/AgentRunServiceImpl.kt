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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.constant.AGENT_SESSION_ID_PREFIX
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_DEVICE_ID
import com.tencent.bkrepo.agent.pojo.AgentRunRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.service.AgentRunService
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.Preconditions
import io.agentscope.core.agent.RuntimeContext
import io.agentscope.core.event.AgentEvent
import io.agentscope.core.event.AgentResultEvent
import io.agentscope.core.event.ConfirmResult
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
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
class AgentRunServiceImpl(
    private val agent: HarnessAgent,
    private val properties: AgentProperties,
) : AgentRunService {

    override fun createSession(userId: String, deviceId: String?): AgentSessionInfo {
        val sessionId = AGENT_SESSION_ID_PREFIX + StringPool.uniqueId()
        return AgentSessionInfo(
            sessionId = sessionId,
            userId = userId,
            deviceId = deviceId,
            createdDate = LocalDateTime.now(),
        )
    }

    override fun run(userId: String, deviceId: String?, request: AgentRunRequest): SseEmitter {
        validate(request)
        // 会话状态按(userId, sessionId)寻址，userId取自已认证请求，因此伪造sessionId也读不到其他用户的会话
        val runtimeContextBuilder = RuntimeContext.builder()
            .userId(userId)
            .sessionId(request.sessionId)
        deviceId?.takeIf { it.isNotBlank() }?.let {
            runtimeContextBuilder.put(RUNTIME_CONTEXT_DEVICE_ID, it)
        }
        val runtimeContext = runtimeContextBuilder.build()
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        val subscriptionRef = AtomicReference<Disposable>()
        val pendingExternalExecution = AtomicReference(false)
        val sseFinished = AtomicBoolean(false)
        fun finishSse(disposeSubscription: Boolean = true) {
            if (!sseFinished.compareAndSet(false, true)) return
            if (disposeSubscription) subscriptionRef.get()?.dispose()
            emitter.complete()
        }
        val subscription = agent
            .streamEvents(buildResumeMessage(userId, request), runtimeContext)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { event ->
                    try {
                        if (emitAgentEvent(emitter, event, pendingExternalExecution)) {
                            // 本地工具 / HITL 挂起后须结束 SSE，客户端才能执行并续跑
                            finishSse()
                        }
                    } catch (ignored: IOException) {
                        // 客户端已断开，停止本轮推送；会话状态已由AgentStateStore保存，可重连后继续
                        logger.info("agent sse closed by client, session[${request.sessionId}]")
                        finishSse()
                    }
                },
                { error ->
                    logger.error("agent run failed, user[$userId] session[${request.sessionId}]", error)
                    if (sseFinished.compareAndSet(false, true)) {
                        subscriptionRef.get()?.dispose()
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
            emitter.complete()
        }
        return emitter
    }

    private fun validate(request: AgentRunRequest) {
        Preconditions.checkNotBlank(request.sessionId, "sessionId")
        Preconditions.checkArgument(request.sessionId.length <= properties.maxSessionIdLength, "sessionId")
        val hasContent = request.content.isNotBlank()
        val hasConfirm = !request.confirmResults.isNullOrEmpty()
        val hasExternal = !request.externalExecutionResults.isNullOrEmpty()
        Preconditions.checkArgument(hasContent || hasConfirm || hasExternal, "content, confirmResults or externalExecutionResults")
        if (hasContent) {
            Preconditions.checkArgument(request.content.length <= properties.maxMessageLength, "content")
        }
    }

    /**
     * @return true 表示本轮 SSE 应结束，等待客户端回传 confirmResults / externalExecutionResults
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
        return event.type.value == "REQUIRE_USER_CONFIRM"
    }

    private fun buildResumeMessage(userId: String, request: AgentRunRequest): Msg {
        val confirmResults = request.confirmResults
        if (!confirmResults.isNullOrEmpty()) {
            val results = confirmResults.map { dto ->
                ConfirmResult(
                    dto.confirmed,
                    ToolUseBlock.builder()
                        .id(dto.callId)
                        .name(dto.toolName)
                        .input(dto.toolInput)
                        .build(),
                )
            }
            return UserMessage.builder()
                .name(userId)
                .textContent(request.content.ifBlank { " " })
                .metadata(mapOf(Msg.METADATA_CONFIRM_RESULTS to results))
                .build()
        }
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
