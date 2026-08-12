/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

/** 单条待恢复 interrupt 快照。 */
data class PendingInterruptSnapshot(
    val id: String,
    val reason: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val requiresApproval: Boolean = false,
)

/** thread 级 pending interrupt 状态，供 resume 校验。 */
data class PendingInterruptSession(
    val originRunId: String,
    val interrupts: List<PendingInterruptSnapshot>,
)

interface AgentPendingInterruptStore {

    fun save(sessionId: String, session: PendingInterruptSession)

    fun get(sessionId: String): PendingInterruptSession?

    fun clear(sessionId: String)
}
