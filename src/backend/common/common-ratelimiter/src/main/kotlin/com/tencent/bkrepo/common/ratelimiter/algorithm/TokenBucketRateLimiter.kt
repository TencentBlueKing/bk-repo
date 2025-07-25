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

/**
 * 单机令牌桶算法实现
 */
class TokenBucketRateLimiter(
    private val permitsPerSecond: Double,
    private val keepConnection: Boolean = true,
) : RateLimiter {

    private val guavaRateLimiter = com.google.common.util.concurrent.RateLimiter.create(permitsPerSecond)

    override fun tryAcquire(permits: Long): Boolean {
        try {
            return guavaRateLimiter.tryAcquire(permits.toInt(), TRY_LOCK_TIMEOUT, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            throw AcquireLockFailedException("lock acquire failed: $e")
        }
    }

    override fun removeCacheLimit(key: String) {
        // 非redis类实现不需要处理
    }

    override fun getLimitPerSecond(): Long {
        return permitsPerSecond.toLong()
    }

    override fun keepConnection(): Boolean {
        return keepConnection
    }
}
