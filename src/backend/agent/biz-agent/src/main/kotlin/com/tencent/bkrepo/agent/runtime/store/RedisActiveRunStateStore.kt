/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime.store

import com.tencent.bkrepo.agent.constant.AGENT_ACTIVE_RUN_KEY_PREFIX
import com.tencent.bkrepo.agent.constant.AGENT_RUN_CANCEL_KEY_PREFIX
import com.tencent.bkrepo.agent.constant.AGENT_RUN_LOCK_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import java.util.concurrent.ConcurrentHashMap

class RedisActiveRunStateStore(
    private val redisOperation: RedisOperation,
    private val lockTtlSeconds: Long,
    private val activeTtlSeconds: Long,
) : ActiveRunStateStore {

    private val activeLocks = ConcurrentHashMap<String, RedisLock>()

    override fun tryAcquireLock(userId: String, threadId: String): Boolean {
        val key = lockKey(userId, threadId)
        val lock = RedisLock(redisOperation, key, lockTtlSeconds)
        if (!lock.tryLock()) {
            return false
        }
        activeLocks[key] = lock
        return true
    }

    override fun releaseLock(userId: String, threadId: String) {
        activeLocks.remove(lockKey(userId, threadId))?.unlock()
    }

    override fun isLockHeld(userId: String, threadId: String): Boolean {
        return redisOperation.get(lockKey(userId, threadId)) != null
    }

    override fun bindActiveRun(userId: String, threadId: String, runId: String) {
        redisOperation.set(activeRunKey(userId, threadId), runId, activeTtlSeconds)
    }

    override fun getActiveRunId(userId: String, threadId: String): String? {
        return redisOperation.get(activeRunKey(userId, threadId))
    }

    override fun clearActiveRun(userId: String, threadId: String) {
        redisOperation.delete(activeRunKey(userId, threadId))
    }

    override fun requestStop(runId: String) {
        redisOperation.set(stopKey(runId), "1", activeTtlSeconds)
    }

    override fun isStopRequested(runId: String): Boolean {
        return redisOperation.get(stopKey(runId)) != null
    }

    override fun clearStop(runId: String) {
        redisOperation.delete(stopKey(runId))
    }

    private fun lockKey(userId: String, threadId: String): String =
        "$AGENT_RUN_LOCK_KEY_PREFIX$userId:$threadId"

    private fun activeRunKey(userId: String, threadId: String): String =
        "$AGENT_ACTIVE_RUN_KEY_PREFIX$userId:$threadId"

    private fun stopKey(runId: String): String = "$AGENT_RUN_CANCEL_KEY_PREFIX$runId"
}
