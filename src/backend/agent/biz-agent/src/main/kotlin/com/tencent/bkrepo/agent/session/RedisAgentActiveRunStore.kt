/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.constant.AGENT_ACTIVE_RUN_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisOperation

class RedisAgentActiveRunStore(
    private val redisOperation: RedisOperation,
    private val ttlSeconds: Long,
) : AgentActiveRunStore {

    override fun bind(userId: String, threadId: String, runId: String) {
        redisOperation.set(key(userId, threadId), runId, ttlSeconds)
    }

    override fun get(userId: String, threadId: String): String? =
        redisOperation.get(key(userId, threadId))

    override fun clear(userId: String, threadId: String) {
        redisOperation.delete(key(userId, threadId))
    }

    private fun key(userId: String, threadId: String): String =
        "$AGENT_ACTIVE_RUN_KEY_PREFIX$userId:$threadId"
}
