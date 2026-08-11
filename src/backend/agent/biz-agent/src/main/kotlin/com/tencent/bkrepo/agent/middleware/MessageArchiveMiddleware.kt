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

package com.tencent.bkrepo.agent.middleware

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_FORCE_ARCHIVE_ASSISTANT
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_MESSAGE_ARCHIVE_STATE
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_RUN_ID
import com.tencent.bkrepo.agent.service.AgentMessageArchiveService
import io.agentscope.core.agent.Agent
import io.agentscope.core.agent.RuntimeContext
import io.agentscope.core.event.AgentEvent
import io.agentscope.core.event.AgentResultEvent
import io.agentscope.core.event.TextBlockDeltaEvent
import io.agentscope.core.message.GenerateReason
import io.agentscope.core.message.Msg
import io.agentscope.core.message.TextBlock
import io.agentscope.core.message.ToolUseBlock
import io.agentscope.core.message.UserMessage
import io.agentscope.core.middleware.AgentInput
import io.agentscope.core.middleware.MiddlewareBase
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType
import java.util.function.Function

/**
 * 通过 [onAgent] 将 USER/ASSISTANT 原文归档到 Mongo，与 [AgentMessageArchiveService] 对接。
 *
 * runId 与 sessionId 来自 [RuntimeContext]；本地工具挂起时不归档半成品 assistant。
 */
@Component
class MessageArchiveMiddleware(
    private val agentMessageArchiveService: AgentMessageArchiveService,
    private val agentProperties: AgentProperties,
) : MiddlewareBase {

    override fun onAgent(
        agent: Agent,
        ctx: RuntimeContext,
        input: AgentInput,
        next: Function<AgentInput, Flux<AgentEvent>>,
    ): Flux<AgentEvent> {
        val sessionId = ctx.getSessionId()?.takeIf { it.isNotBlank() }
        val runId = ctx.get(RUNTIME_CONTEXT_RUN_ID) as? String
        if (sessionId == null || runId.isNullOrBlank()) {
            return next.apply(input)
        }

        extractUserText(input.msgs)?.let { userText ->
            agentMessageArchiveService.archiveUserMessage(sessionId, runId, userText)
        }

        val archiveState = MessageArchiveState()
        ctx.put(RUNTIME_CONTEXT_MESSAGE_ARCHIVE_STATE, archiveState)

        return next.apply(input)
            .doOnNext { event -> trackEvent(event, archiveState) }
            .doFinally { signal -> finalizeArchive(ctx, sessionId, runId, archiveState, signal) }
    }

    private fun trackEvent(event: AgentEvent, archiveState: MessageArchiveState) {
        if (event is TextBlockDeltaEvent) {
            archiveState.assistantText.append(event.delta)
        }
        if (event is AgentResultEvent) {
            val result = event.result ?: return
            if (result.generateReason != GenerateReason.TOOL_SUSPENDED) {
                return
            }
            val toolCalls = result.getContentBlocks(ToolUseBlock::class.java)
            if (toolCalls.isNotEmpty()) {
                archiveState.skipAssistantArchive = true
            }
        }
    }

    private fun finalizeArchive(
        ctx: RuntimeContext,
        sessionId: String,
        runId: String,
        archiveState: MessageArchiveState,
        signal: SignalType,
    ) {
        if (archiveState.skipAssistantArchive || archiveState.assistantText.isEmpty()) {
            return
        }
        val forceOnCancel = ctx.get(RUNTIME_CONTEXT_FORCE_ARCHIVE_ASSISTANT) as? Boolean == true
        val shouldArchive = when (signal) {
            SignalType.ON_COMPLETE -> true
            SignalType.CANCEL -> forceOnCancel
            else -> false
        }
        if (!shouldArchive) {
            return
        }
        agentMessageArchiveService.archiveAssistantMessage(
            sessionId = sessionId,
            runId = runId,
            content = archiveState.assistantText.toString(),
            agentId = agentProperties.name,
        )
    }

    private fun extractUserText(msgs: List<Msg>): String? {
        val userMessage = msgs.filterIsInstance<UserMessage>().firstOrNull() ?: return null
        val text = userMessage.getContentBlocks(TextBlock::class.java)
            .joinToString(separator = "") { block -> block.text ?: "" }
        return text.takeIf { it.isNotBlank() }
    }

    private class MessageArchiveState {
        val assistantText = StringBuilder()
        var skipAssistantArchive = false
    }
}
