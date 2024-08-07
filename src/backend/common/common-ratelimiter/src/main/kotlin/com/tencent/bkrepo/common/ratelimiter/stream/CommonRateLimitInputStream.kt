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

package com.tencent.bkrepo.common.ratelimiter.stream

import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.OverloadException
import java.io.InputStream

class CommonRateLimitInputStream(
    delegate: InputStream,
    private val rateLimiter: RateLimiter,
    private val sleepTime: Long,
    private val retryNum: Int,
    private val rangeLength: Long? = null,
    private val dryRun: Boolean = false,
    private val permitsNum: Long = 1024 * 1024 * 1024,
) : DelegateInputStream(delegate) {

    private var bytesRead: Long = 0
    private var applyNum: Long = 0

    override fun read(): Int {
        tryAcquire(1)
        return super.read()
    }

    override fun read(byteArray: ByteArray): Int {
        tryAcquire(byteArray.size)
        return super.read(byteArray)
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        tryAcquire(len)
        return super.read(byteArray, off, len)
    }

    private fun tryAcquire(bytes: Int) {
        if (rangeLength == null) {
            acquire(bytes.toLong())
        } else {
            // 避免频繁申请，增加耗时，降低申请频率， 每次申请一定数量
            val permits = (rangeLength - bytesRead).coerceAtMost(permitsNum)
            if (bytesRead == 0L || bytesRead / permitsNum > applyNum) {
                acquire(permits)
                applyNum = bytesRead / permitsNum
            }
            bytesRead += bytes
        }
    }

    private fun acquire(permits: Long) {
        var flag = false
        var failedNum = 0
        try {
            while (!flag) {
                // TODO 当限制小于读取大小时，会进入死循环
                flag = rateLimiter.tryAcquire(permits)
                if (!flag && failedNum < retryNum) {
                    failedNum++
                    Thread.sleep(sleepTime)
                }
                if (!flag && failedNum > retryNum) {
                    throw OverloadException("inputstream")
                }
            }
        } catch (e: AcquireLockFailedException) {
            if (dryRun) {
                return
            }
            throw e
        }
    }
}
