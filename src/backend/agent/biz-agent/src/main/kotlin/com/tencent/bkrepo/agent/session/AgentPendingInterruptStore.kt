/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

/**
 * 单条待恢复 interrupt 快照。
 *
 * 字段与 AG-UI [io.agentscope.core.agui.event.AguiEvent.Interrupt] 一一对应，
 * 除 id/reason/toolCallId 外额外持久化 message/responseSchema/expiresAt/metadata，
 * 以便 reconnect 时重放出完整的 interrupt（供前端重新渲染审批 UI），而不是空壳事件。
 */
data class PendingInterruptSnapshot(
    val id: String,
    val reason: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val requiresApproval: Boolean = false,
    val message: String? = null,
    val responseSchema: Map<String, Any?>? = null,
    val expiresAt: String? = null,
    val metadata: Map<String, Any?>? = null,
)

/** thread 级 pending interrupt 状态，供 resume 校验。 */
data class PendingInterruptSession(
    val originRunId: String,
    val interrupts: List<PendingInterruptSnapshot>,
)

interface AgentPendingInterruptStore {

    fun save(threadId: String, session: PendingInterruptSession)

    fun get(threadId: String): PendingInterruptSession?

    fun clear(threadId: String)
}
