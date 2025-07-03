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
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


/**
 * 单机滑动窗口算法实现
 * -- limit: 窗口时间单位内的阈值
 * -- interval: 窗口大小，
 * -- limitUnit 窗口时间单位
 */
class SlidingWindowRateLimiter(
    private val limit: Long,
    private val duration: Duration,
    private val keepConnection: Boolean = true,
) : RateLimiter {

    private val lock: Lock = ReentrantLock()

    /**
     * 子窗口个数
     */
    private val subWindowNum = 10

    /**
     * 窗口列表
     */
    private var windowArray = Array(subWindowNum) { WindowInfo() }
    private val windowSize = duration.toMillis() * subWindowNum

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

    override fun removeCacheLimit(key: String) {
        // 非redis类实现不需要处理
    }

    override fun getLimitPerSecond(): Long {
        return limit / duration.seconds
    }

    override fun keepConnection(): Boolean {
        return keepConnection
    }

    private fun allow(permits: Long): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        // 1. 计算当前时间窗口
        val currentIndex = (currentTimeMillis % windowSize / (windowSize / subWindowNum)).toInt()
        // 2.  更新当前窗口计数 & 重置过期窗口计数
        var sum = 0L
        val windowArrayCopy = windowArray.map { it.copy() }.toTypedArray()
        for (i in windowArray.indices) {
            val windowInfo = windowArray[i]
            if (currentTimeMillis - windowInfo.time > windowSize) {
                windowInfo.count = 0
                windowInfo.time = currentTimeMillis
            }
            if (currentIndex == i && windowInfo.count <= limit) {
                windowInfo.count += permits
            }
            sum += windowInfo.count
        }
        // 3. 当前是否超过限制
        return if (sum <= limit) {
            true
        } else {
            //如果最终sum>limit, windowInfo.count需要减掉permits
            windowArray = windowArrayCopy
            false
        }
    }

    data class WindowInfo(
        var time: Long = System.currentTimeMillis(),
        var count: Long = 0,
    )

}
