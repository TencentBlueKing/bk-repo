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
        val subscription = agent
            .streamEvents(UserMessage(userId, request.content), runtimeContext)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { event ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .id(event.id)
                                .name(event.type.value)
                                .data(event, MediaType.APPLICATION_JSON)
                        )
                    } catch (ignored: IOException) {
                        // 客户端已断开，停止本轮推送；会话状态已由AgentStateStore保存，可重连后继续
                        logger.info("agent sse closed by client, session[${request.sessionId}]")
                        subscriptionRef.get()?.dispose()
                        emitter.complete()
                    }
                },
                { error ->
                    logger.error("agent run failed, user[$userId] session[${request.sessionId}]", error)
                    emitter.completeWithError(error)
                },
                { emitter.complete() },
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
        Preconditions.checkNotBlank(request.content, "content")
        Preconditions.checkArgument(request.content.length <= properties.maxMessageLength, "content")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentRunServiceImpl::class.java)
    }
}
