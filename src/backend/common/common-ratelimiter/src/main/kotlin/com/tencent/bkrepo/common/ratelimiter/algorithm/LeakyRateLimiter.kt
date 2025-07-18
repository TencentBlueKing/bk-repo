/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


/**
 * 单机漏桶算法实现
 */
class LeakyRateLimiter(
    private val rate: Double,
    private val capacity: Long,
    private val keepConnection: Boolean = true,
) : RateLimiter {

    // 计算的起始时间
    private var lastLeakTime = System.currentTimeMillis()
    private var water: Long = 0
    private val lock: Lock = ReentrantLock()


    override fun tryAcquire(permits: Long): Boolean {
        try {
            if (!lock.tryLock(TRY_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw AcquireLockFailedException("leaky tryLock wait too long: $TRY_LOCK_TIMEOUT ms")
            }
            try {
                return allow(permits)
            } finally {
                lock.unlock()
            }
        } catch (e: InterruptedException) {
            throw AcquireLockFailedException("leaky tryLock is interrupted by lock timeout: $e")
        }
    }

    override fun removeCacheLimit(key: String) {
        // 非redis类实现不需要处理
    }

    override fun getLimitPerSecond(): Long {
        return (rate * capacity).toLong()
    }

    override fun keepConnection(): Boolean {
        return keepConnection
    }

    private fun allow(permits: Long): Boolean {
        if (water == 0L) {
            lastLeakTime = System.currentTimeMillis()
            water += permits
            return true
        }
        val waterLeaked: Double = ((System.currentTimeMillis() - lastLeakTime) / 1000) * rate
        val waterLeft = (water - waterLeaked).toLong()
        water = Math.max(0, waterLeft) // 漏水
        lastLeakTime = System.currentTimeMillis()
        if (water + permits <= capacity) {
            water += permits
            return true
        }
        return false
    }
}
