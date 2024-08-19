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
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.OverloadException
import java.io.InputStream

class CommonRateLimitInputStream(
    delegate: InputStream,
    private val rateCheckContext: RateCheckContext
) : DelegateInputStream(delegate) {

    private var bytesRead: Long = 0
    private var applyNum: Long = 0

    override fun read(): Int {
        tryAcquire(1)
        val data = super.read()
        if (data != -1) {
            bytesRead++
        }
        return data
    }

    override fun read(byteArray: ByteArray): Int {
        tryAcquire(byteArray.size)
        val readLen = super.read(byteArray)
        if (readLen != -1) {
            bytesRead += readLen
        }
        return readLen
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        tryAcquire(len)
        val readLen = super.read(byteArray, off, len)
        if (readLen != -1) {
            bytesRead += readLen
        }
        return readLen
    }

    private fun tryAcquire(bytes: Int) {
        with(rateCheckContext) {
            if (rangeLength == null || rangeLength!! <= 0) {
                // 当不知道文件大小时，没办法进行大小预估，无法降低申请频率， 只能每次读取都进行判断
                acquire(bytes.toLong())
            } else {
                // 避免频繁申请，增加耗时，降低申请频率， 每次申请一定数量
                if (bytesRead == 0L || (bytesRead / permitsOnce) > applyNum) {
                    val permits = (rangeLength!! - bytesRead).coerceAtMost(permitsOnce)
                    acquire(permits)
                    applyNum = bytesRead / permitsOnce
                }
            }
        }
    }

    private fun acquire(permits: Long) {
        var flag = false
        var failedNum = 0
        while (!flag) {
            // 当限制小于读取大小时，会进入死循环，增加等待轮次，如果达到等待轮次上限后还是无法获取，则抛异常结束
            try {
                flag = rateCheckContext.rateLimiter.tryAcquire(permits)
            } catch (ignore: AcquireLockFailedException) {
                return
            }
            if (!flag && failedNum < rateCheckContext.waitRound) {
                failedNum++
                try {
                    Thread.sleep(rateCheckContext.latency)
                } catch (ignore: InterruptedException) {
                }
            }
            if (!flag && failedNum >= rateCheckContext.waitRound) {
                if (rateCheckContext.dryRun) {
                    return
                }
                throw OverloadException("request reached bandwidth limit")
            }
        }
    }
}
