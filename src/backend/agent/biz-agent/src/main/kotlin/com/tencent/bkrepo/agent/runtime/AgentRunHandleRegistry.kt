/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import io.agentscope.core.agent.RuntimeContext
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** 本 JVM 内 active run 句柄，供 stop API 直接 interrupt 与 finish SSE。 */
@Component
class AgentRunHandleRegistry {

    data class Handle(
        val userId: String,
        val sessionId: String,
        val runId: String,
        val runtimeContext: RuntimeContext,
        val abort: () -> Unit,
    )

    private val bySession = ConcurrentHashMap<String, Handle>()

    fun register(handle: Handle) {
        bySession[key(handle.userId, handle.sessionId)] = handle
    }

    fun remove(userId: String, sessionId: String) {
        bySession.remove(key(userId, sessionId))
    }

    fun find(userId: String, sessionId: String): Handle? = bySession[key(userId, sessionId)]

    private fun key(userId: String, sessionId: String): String = "$userId:$sessionId"
}
