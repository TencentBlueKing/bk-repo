/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.agent.constant.AGENT_PENDING_INTERRUPT_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisOperation

class RedisAgentPendingInterruptStore(
    private val redisOperation: RedisOperation,
    private val objectMapper: ObjectMapper,
    private val ttlSeconds: Long,
) : AgentPendingInterruptStore {

    override fun save(threadId: String, session: PendingInterruptSession) {
        redisOperation.set(key(threadId), objectMapper.writeValueAsString(session), ttlSeconds)
    }

    override fun get(threadId: String): PendingInterruptSession? {
        val raw = redisOperation.get(key(threadId)) ?: return null
        return objectMapper.readValue(raw, PendingInterruptSession::class.java)
    }

    override fun clear(threadId: String) {
        redisOperation.delete(key(threadId))
    }

    private fun key(threadId: String): String = "$AGENT_PENDING_INTERRUPT_KEY_PREFIX$threadId"
}
