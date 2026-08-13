/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.context

import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_DEVICE_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_EXECUTION_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_PROJECT_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_RUN_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_TRACE_ID
import io.agentscope.core.agent.RuntimeContext

/**
 * 单次 AG-UI run 的可信服务端上下文。
 *
 * 身份字段（userId、projectId）来自 HTTP 认证；deviceId/traceId 经白名单 forwardedProps 提取；
 * 不信任 [io.agentscope.core.agui.model.RunAgentInput.context] 中的身份键。
 */
data class AgentChatContext(
    val userId: String,
    val projectId: String,
    val threadId: String,
    val runId: String,
    val executionId: String,
    val deviceId: String?,
    val traceId: String?,
) {
    fun toRuntimeContext(): RuntimeContext {
        val builder = RuntimeContext.builder()
            .userId(userId)
            .sessionId(threadId)
            .put(RUNTIME_CONTEXT_PROJECT_ID, projectId)
            .put(RUNTIME_CONTEXT_RUN_ID, runId)
            .put(RUNTIME_CONTEXT_EXECUTION_ID, executionId)
        deviceId?.let { builder.put(RUNTIME_CONTEXT_DEVICE_ID, it) }
        traceId?.let { builder.put(RUNTIME_CONTEXT_TRACE_ID, it) }
        return builder.build()
    }
}
