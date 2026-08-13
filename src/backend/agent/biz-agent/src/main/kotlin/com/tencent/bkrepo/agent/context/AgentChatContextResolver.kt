/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.context

import com.tencent.bkrepo.agent.agui.AgentForwardedPropsKeys
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

@Component
class AgentChatContextResolver {

    fun resolve(
        userId: String,
        projectId: String,
        input: RunAgentInput,
        executionId: String,
    ): AgentChatContext {
        val transport = extractTransport(input)
        return AgentChatContext(
            userId = userId,
            projectId = projectId,
            threadId = input.threadId,
            runId = input.runId,
            executionId = executionId,
            deviceId = transport.deviceId,
            traceId = transport.traceId,
        )
    }

    private data class TransportMetadata(
        val deviceId: String?,
        val traceId: String?,
    )

    private fun extractTransport(input: RunAgentInput): TransportMetadata {
        val props = asStringMap(input.forwardedProps) ?: return TransportMetadata(null, null)
        return TransportMetadata(
            deviceId = readString(props, AgentForwardedPropsKeys.DEVICE_ID),
            traceId = readString(props, AgentForwardedPropsKeys.TRACE_ID),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun asStringMap(value: Any?): Map<String, Any>? {
        if (value == null) return null
        if (value !is Map<*, *>) return null
        return value.entries
            .mapNotNull { (key, v) ->
                val name = key?.toString()?.trim().orEmpty()
                if (name.isBlank()) null else name to v as Any
            }
            .toMap()
            .takeIf { it.isNotEmpty() }
    }

    private fun readString(props: Map<String, Any>, key: String): String? {
        return props[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
