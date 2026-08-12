/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import java.util.concurrent.ConcurrentHashMap

class InMemoryAgentPendingInterruptStore : AgentPendingInterruptStore {

    private val sessions = ConcurrentHashMap<String, PendingInterruptSession>()

    override fun save(sessionId: String, session: PendingInterruptSession) {
        sessions[sessionId] = session
    }

    override fun get(sessionId: String): PendingInterruptSession? = sessions[sessionId]

    override fun clear(sessionId: String) {
        sessions.remove(sessionId)
    }
}
