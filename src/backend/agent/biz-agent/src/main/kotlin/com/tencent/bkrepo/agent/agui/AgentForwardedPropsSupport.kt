/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import io.agentscope.core.agui.model.RunAgentInput

/** [RunAgentInput.forwardedProps] 白名单键：仅允许客户端传递设备与追踪元数据。 */
object AgentForwardedPropsKeys {
    const val DEVICE_ID = "deviceId"
    const val TRACE_ID = "traceId"
}

/**
 * 从 AG-UI [RunAgentInput.forwardedProps] 提取运行元数据。
 *
 * 身份字段（userId、projectId 等）必须由服务端认证链路注入，忽略客户端同名键。
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

    data class Extracted(
        val deviceId: String?,
        val traceId: String?,
    )

    fun extract(input: RunAgentInput): Extracted {
        val props = asStringMap(input.forwardedProps) ?: return Extracted(null, null)
        return Extracted(
            deviceId = readString(props, AgentForwardedPropsKeys.DEVICE_ID),
            traceId = readString(props, AgentForwardedPropsKeys.TRACE_ID),
        )
    }

    /** 去掉不可信身份键与已服务端接管的 transport 键，剩余 forwardedProps 原样留给 adapter。 */
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

    private fun readString(props: Map<String, Any>, key: String): String? {
        return props[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}
