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

    override fun bind(userId: String, threadId: String, runId: String) {
        runs[key(userId, threadId)] = runId
    }

    override fun get(userId: String, threadId: String): String? = runs[key(userId, threadId)]

    override fun clear(userId: String, threadId: String) {
        runs.remove(key(userId, threadId))
    }

    private fun key(userId: String, threadId: String): String = "$userId:$threadId"
}
