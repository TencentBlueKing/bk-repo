/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.constant.AGENT_RESUME_IDEMPOTENCY_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisOperation

class RedisAgentResumeIdempotencyStore(
    private val redisOperation: RedisOperation,
    private val ttlSeconds: Long,
) : AgentResumeIdempotencyStore {

    override fun tryMark(sessionId: String, interruptId: String, fingerprint: String): Boolean {
        val key = "$AGENT_RESUME_IDEMPOTENCY_KEY_PREFIX$sessionId:$interruptId:$fingerprint"
        val existing = redisOperation.getAndSet(key, "1", ttlSeconds)
        return existing == null
    }
}
