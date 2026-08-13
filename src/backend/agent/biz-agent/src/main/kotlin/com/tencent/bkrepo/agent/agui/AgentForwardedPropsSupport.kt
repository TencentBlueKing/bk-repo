/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

/** [RunAgentInput.forwardedProps] 白名单键：仅允许客户端传递设备与追踪元数据。 */
object AgentForwardedPropsKeys {
    const val DEVICE_ID = "deviceId"
    const val TRACE_ID = "traceId"
}

/**
 * 清洗 AG-UI [RunAgentInput.forwardedProps]：去掉不可信身份键与已由服务端接管的 transport 键。
 *
 * deviceId/traceId 提取见 [com.tencent.bkrepo.agent.context.AgentChatContextResolver]。
 */
object AgentForwardedPropsSupport {

    private val BLOCKED_KEYS = setOf(
        "userId",
        "projectId",
        "ticket",
        "token",
        AgentForwardedPropsKeys.DEVICE_ID,
        AgentForwardedPropsKeys.TRACE_ID,
    )

    fun sanitizeForwardedProps(forwardedProps: Any?): Map<String, Any>? {
        val props = asStringMap(forwardedProps) ?: return null
        val sanitized = props.filterKeys { key -> key !in BLOCKED_KEYS }
        return sanitized.takeIf { it.isNotEmpty() }
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
}
