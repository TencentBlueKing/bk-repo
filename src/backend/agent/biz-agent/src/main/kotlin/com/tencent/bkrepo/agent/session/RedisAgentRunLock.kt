/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.constant.AGENT_RUN_LOCK_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 [RedisLock] 的 run 互斥实现。
 *
 * 锁实例按 lockKey 缓存在 [ConcurrentHashMap] 中，以便 Reactor 异步线程释放（不可使用 ThreadLocal）。
 */
class RedisAgentRunLock(
    private val redisOperation: RedisOperation,
    private val lockTtlSeconds: Long,
) : AgentRunLock {

    private val activeLocks = ConcurrentHashMap<String, RedisLock>()

    override fun tryAcquire(userId: String, sessionId: String): Boolean {
        val key = lockKey(userId, sessionId)
        val lock = RedisLock(redisOperation, key, lockTtlSeconds)
        if (!lock.tryLock()) {
            return false
        }
        activeLocks[key] = lock
        return true
    }

    override fun release(userId: String, sessionId: String) {
        activeLocks.remove(lockKey(userId, sessionId))?.unlock()
    }

    override fun isRunning(userId: String, sessionId: String): Boolean {
        return redisOperation.get(lockKey(userId, sessionId)) != null
    }

    private fun lockKey(userId: String, sessionId: String): String {
        return "$AGENT_RUN_LOCK_KEY_PREFIX$userId:$sessionId"
    }
}
