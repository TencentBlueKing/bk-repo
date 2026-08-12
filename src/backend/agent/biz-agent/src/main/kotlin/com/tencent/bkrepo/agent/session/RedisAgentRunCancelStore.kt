/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.constant.AGENT_RUN_CANCEL_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisOperation

class RedisAgentRunCancelStore(
    private val redisOperation: RedisOperation,
    private val ttlSeconds: Long,
) : AgentRunCancelStore {

    override fun requestCancel(runId: String) {
        redisOperation.set(key(runId), "1", ttlSeconds)
    }

    override fun isCancelled(runId: String): Boolean = redisOperation.get(key(runId)) != null

    override fun clear(runId: String) {
        redisOperation.delete(key(runId))
    }

    private fun key(runId: String): String = "$AGENT_RUN_CANCEL_KEY_PREFIX$runId"
}
