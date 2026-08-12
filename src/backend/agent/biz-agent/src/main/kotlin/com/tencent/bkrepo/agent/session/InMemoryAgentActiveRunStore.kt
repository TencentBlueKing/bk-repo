/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import java.util.concurrent.ConcurrentHashMap

class InMemoryAgentActiveRunStore : AgentActiveRunStore {

    private val runs = ConcurrentHashMap<String, String>()

    override fun bind(userId: String, sessionId: String, runId: String) {
        runs[key(userId, sessionId)] = runId
    }

    override fun get(userId: String, sessionId: String): String? = runs[key(userId, sessionId)]

    override fun clear(userId: String, sessionId: String) {
        runs.remove(key(userId, sessionId))
    }

    private fun key(userId: String, sessionId: String): String = "$userId:$sessionId"
}
