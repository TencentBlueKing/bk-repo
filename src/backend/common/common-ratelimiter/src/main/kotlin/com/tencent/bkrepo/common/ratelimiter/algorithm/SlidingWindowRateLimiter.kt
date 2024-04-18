/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.ratelimiter.algorithm

import com.tencent.bkrepo.common.ratelimiter.constant.TRY_LOCK_TIMEOUT
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * 单机令牌桶算法实现
 */
class SlidingWindowRateLimiter(
    private val limit: Long,
    private val interval: Long,
    private val limitUnit: TimeUnit,
) : RateLimiter {

    private val queue = LinkedList<Long>()
    private var counter: Long = 0
    private var lastUpdate = System.currentTimeMillis()
    private val lock: Lock = ReentrantLock()

    override fun tryAcquire(permits: Long): Boolean {
        try {
            if (!lock.tryLock(TRY_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw AcquireLockFailedException("sliding window tryLock wait too long: $TRY_LOCK_TIMEOUT ms")
            }
            try {
                return allow(permits)
            } finally {
                lock.unlock()
            }
        } catch (e: InterruptedException) {
            throw AcquireLockFailedException("sliding window tryLock is interrupted by lock timeout: $e")
        }
    }

    fun allow(permits: Long): Boolean {
        val now = System.currentTimeMillis()
        val slots = ((now - lastUpdate) / (interval / limitUnit.toMillis(1))).toInt()

        // 移除过期的时间片
        while (queue.size >= slots) {
            val removed = queue.removeFirst()
            counter -= removed
        }

        // 添加新的时间片
        for (i in queue.size until slots) {
            queue.addLast(0)
        }

        // 如果当前时间片的请求数量已经达到窗口大小，则拒绝请求
        if (counter + permits > limit) {
            return false
        }

        // 更新当前时间片的请求数量，并增加计数器
        queue.addLast(queue.removeLast() + permits)
        counter += permits
        lastUpdate = now
        return true
    }

}
