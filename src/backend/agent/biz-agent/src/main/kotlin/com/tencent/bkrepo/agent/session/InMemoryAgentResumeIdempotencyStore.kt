/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import java.util.concurrent.ConcurrentHashMap

class InMemoryAgentResumeIdempotencyStore : AgentResumeIdempotencyStore {

    private val keys = ConcurrentHashMap.newKeySet<String>()

    override fun tryMark(threadId: String, interruptId: String, fingerprint: String): Boolean {
        return keys.add("$threadId:$interruptId:$fingerprint")
    }
}
