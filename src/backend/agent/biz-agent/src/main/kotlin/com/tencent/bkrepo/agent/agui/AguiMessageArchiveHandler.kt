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

package com.tencent.bkrepo.agent.agui

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.service.AgentMessageArchiveService
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.MessageContent
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

/**
 * 在 AG-UI 事件层归档 USER/ASSISTANT 消息（手册 §17.3）。
 *
 * USER 以 [RunAgentInput.messages] 中的 canonical id 写入；ASSISTANT 以
 * [AguiEvent.TextMessageStart.messageId] 聚合 delta。本地工具挂起（interrupt）时不归档半成品 assistant。
 */
@Component
class AguiMessageArchiveHandler(
    private val agentMessageArchiveService: AgentMessageArchiveService,
    private val agentProperties: EffectiveAgentRuntimeProperties,
    private val objectMapper: ObjectMapper,
) {

    /** 单次 run 的归档缓冲状态。 */
    class State {
        var skipAssistantArchive: Boolean = false
        var currentAssistant: AssistantBuffer? = null

        data class AssistantBuffer(
            val messageId: String,
            val text: StringBuilder = StringBuilder(),
        )
    }

    fun archiveIncomingUserMessages(input: RunAgentInput, threadId: String, runId: String) {
        if (!input.hasMessages()) return
        for (message in input.messages) {
            if (!message.isUserMessage()) continue
            val text = message.textContent?.takeIf { it.isNotBlank() } ?: continue
            agentMessageArchiveService.archiveUserMessage(
                threadId = threadId,
                runId = runId,
                messageId = message.id,
                textContent = text,
                structuredContent = toStructuredMap(message.content),
            )
        }
    }

    fun onEvent(event: AguiEvent, threadId: String, runId: String, state: State) {
        when (event) {
            is AguiEvent.TextMessageStart -> {
                state.currentAssistant = State.AssistantBuffer(event.messageId())
            }
            is AguiEvent.TextMessageContent -> {
                state.currentAssistant?.text?.append(event.delta())
            }
            is AguiEvent.TextMessageEnd -> {
                finalizeAssistant(threadId, runId, state)
            }
            is AguiEvent.RunFinished -> {
                if (event.outcome() is AguiEvent.RunFinishedInterruptOutcome) {
                    state.skipAssistantArchive = true
                    state.currentAssistant = null
                } else {
                    finalizeAssistant(threadId, runId, state)
                }
            }
            is AguiEvent.RunError -> {
                state.skipAssistantArchive = true
                state.currentAssistant = null
            }
            else -> Unit
        }
    }

    /** SSE 断开且已请求强制归档时，尽力保存已流出的 assistant 文本。 */
    fun forceFinalizeAssistant(threadId: String, runId: String, state: State) {
        if (state.skipAssistantArchive) return
        finalizeAssistant(threadId, runId, state)
    }

    private fun finalizeAssistant(threadId: String, runId: String, state: State) {
        if (state.skipAssistantArchive) return
        val buffer = state.currentAssistant ?: return
        state.currentAssistant = null
        val text = buffer.text.toString()
        if (text.isBlank()) return
        agentMessageArchiveService.archiveAssistantMessage(
            threadId = threadId,
            runId = runId,
            messageId = buffer.messageId,
            textContent = text,
            agentId = agentProperties.name,
            structuredContent = mapOf("type" to "text", "text" to text),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toStructuredMap(content: MessageContent?): Map<String, Any>? {
        if (content == null) return null
        // AG-UI user message content 允许 plain string（见 UserMessageSchema.content:
        // z.union([z.string(), z.array(InputContentSchema)]））。AgentScope 反序列化后
        // 这里可能是 String，不能直接 convertValue 成 Map。
        if (content is String) {
            return mapOf("type" to "text", "text" to content)
        }
        if (content is Map<*, *>) {
            return content.entries
                .mapNotNull { (key, value) ->
                    key?.toString()?.takeIf { it.isNotBlank() }?.let { it to value as Any }
                }
                .toMap()
                .takeIf { it.isNotEmpty() }
        }
        if (content is List<*>) {
            return mapOf("type" to "multipart", "parts" to content)
        }
        return runCatching {
            objectMapper.convertValue(content, Map::class.java) as Map<String, Any>
        }.getOrNull()
    }
}
