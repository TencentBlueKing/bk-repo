/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.hitl

import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import com.tencent.bkrepo.agent.session.AgentResumeIdempotencyStore
import com.tencent.bkrepo.agent.session.PendingInterruptSession
import org.springframework.stereotype.Component

/**
 * HITL 运行态统一入口：pending interrupt 与 resume 幂等。
 *
 * 主线 run 编排只依赖此接口，具体存储由 session 层 Redis/InMemory 实现。
 */
interface AgentInterruptStateRepository {

    fun savePendingInterrupt(threadId: String, session: PendingInterruptSession)

    fun getPendingInterrupt(threadId: String): PendingInterruptSession?

    fun clearPendingInterrupt(threadId: String)

    /** @return true 表示首次见到该 resume 指纹 */
    fun tryMarkResume(threadId: String, interruptId: String, fingerprint: String): Boolean
}

@Component
class DefaultAgentInterruptStateRepository(
    private val pendingInterruptStore: AgentPendingInterruptStore,
    private val resumeIdempotencyStore: AgentResumeIdempotencyStore,
) : AgentInterruptStateRepository {

    override fun savePendingInterrupt(threadId: String, session: PendingInterruptSession) {
        pendingInterruptStore.save(threadId, session)
    }

    override fun getPendingInterrupt(threadId: String): PendingInterruptSession? =
        pendingInterruptStore.get(threadId)

    override fun clearPendingInterrupt(threadId: String) {
        pendingInterruptStore.clear(threadId)
    }

    override fun tryMarkResume(threadId: String, interruptId: String, fingerprint: String): Boolean =
        resumeIdempotencyStore.tryMark(threadId, interruptId, fingerprint)
}
