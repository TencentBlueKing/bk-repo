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

import com.google.common.base.Stopwatch
import com.tencent.bkrepo.common.ratelimiter.constant.TRY_LOCK_TIMEOUT
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * 单机固定窗口算法实现
 */
class FixedWindowRateLimiter(
    private val limit: Long,
    private val duration: Duration,
    private val stopWatch: Stopwatch = Stopwatch.createStarted(),
    private val keepConnection: Boolean = true,
) : RateLimiter {

    private var currentValue: Long = 0
    private val lock: Lock = ReentrantLock()

    override fun tryAcquire(permits: Long): Boolean {
        // TODO 当剩余容量少于permit时，会导致一直获取不到
        try {
            if (!lock.tryLock(TRY_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw AcquireLockFailedException("fix window tryLock wait too long: $TRY_LOCK_TIMEOUT ms")
            }
            try {
                if (stopWatch.elapsed(TimeUnit.MILLISECONDS) > duration.toMillis()) {
                    currentValue = 0
                    stopWatch.reset()
                }
                if (currentValue + permits > limit) return false
                currentValue += permits
                return true
            } finally {
                lock.unlock()
            }
        } catch (e: InterruptedException) {
            throw AcquireLockFailedException("fix window tryLock is interrupted by lock timeout: $e")
        }
    }

    override fun removeCacheLimit(key: String) {
        // 非redis类实现不需要处理
    }

    override fun getLimitPerSecond(): Long {
        return limit / duration.seconds
    }

    override fun keepConnection(): Boolean {
        return keepConnection
    }
}
