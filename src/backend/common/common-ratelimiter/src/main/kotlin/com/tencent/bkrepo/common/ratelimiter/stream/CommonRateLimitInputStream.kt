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

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import java.io.InputStream

/**
 * 限速方式：
 * A：每次尝试申请容量减半，如减半后还是被限速，则继续减半，直至可以获取到
 * B: 每次尝试申请从1K开始，如果申请通过，下次则从2K开始，依次乘以2递增
 * 注意： 需要考虑每次read的字节大小，如果比read字节小，可能需要多次申请才能满足一次读写
 *
 * 如何针对先发起的请求优先获取：
 * 判断文件剩余大小，如果文件已传输一定阈值后，当达到上限时，进行限速， 使用限速方式A进行资源获取
 * 如果文件传输未达到阈值，则使用限速方式B进行资源获取
 * 注意： 阈值如何确定
 *
 * 实际场景下，大部分文件都是小文件，如何快速释放请求，避免连接占用过多
 * 达到限速情况，小文件采用限速方式A进行资源获取
 * 注意： 小文件大小如何界定
 *
 * 先发起请求和小文件请求如何避免冲突
 *
 * 为避免长时间获取不到资源，导致连接断开，需要当等待到一定时间后，需要额外放通一次最小数据
 */

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
        if (rateCheckContext.waitOnLimit) {
            tryAcquireOrWait(bytes)
        } else {
            tryAcquireOrBreak(bytes)
        }
    }

    private fun tryAcquireOrBreak(bytes: Int) {
        with(rateCheckContext) {
            if (rangeLength == null || rangeLength!! <= 0) {
                // 当不知道文件大小时，没办法进行大小预估，无法降低申请频率， 只能每次读取都进行判断
                acquireOrBreak(bytes.toLong())
            } else {
                // 避免频繁申请，增加耗时，降低申请频率， 每次申请一定数量
                // 当申请的bytes比limitPerSecond还大时,直接限流
                if (limitPerSecond < bytes) {
                    if (rateCheckContext.dryRun) {
                        return
                    }
                    throw OverloadException("request reached bandwidth limit")
                }
                // 此处避免限流带宽大小比每次申请的还少的情况下，每次都被限流
                val realPermitOnce = limitPerSecond.coerceAtMost(permitsOnce)
                if (bytesRead == 0L || (bytesRead + bytes) > applyNum) {
                    val leftLength = rangeLength!! - bytesRead
                    val permits = if (leftLength >= 0) {
                        leftLength.coerceAtMost(realPermitOnce)
                    } else {
                        // 当剩余文件大小小于0时，说明文件大小不正确，无法确认剩余多少
                        realPermitOnce
                    }
                    acquireOrBreak(permits)
                    applyNum += permits
                }
            }
        }
    }

    private fun acquireOrBreak(permits: Long) {
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
                    Thread.sleep(rateCheckContext.latency * failedNum)
                } catch (ignore: InterruptedException) {
                }
                continue
            }
            if (!flag && failedNum >= rateCheckContext.waitRound) {
                if (rateCheckContext.dryRun) {
                    return
                }
                throw OverloadException("request reached bandwidth limit")
            }
        }
    }

    private fun tryAcquireOrWait(bytes: Int) {
        with(rateCheckContext) {
            if (rangeLength == null || rangeLength!! <= 0) {
                // 当不知道文件大小时，没办法进行大小预估，无法降低申请频率， 只能每次读取都进行判断
                acquireOrWait(bytes.toLong(), bytes.toLong())
            } else {
                // 避免频繁申请，增加耗时，降低申请频率， 每次申请一定数量
                // 当申请的bytes比limitPerSecond还大时,多次申请组合
                if (limitPerSecond < bytes) {
                    applyNum += acquireOrWait(limitPerSecond, bytes.toLong(), true)
                    return
                }

                if (bytesRead != 0L && bytesRead + bytes <= applyNum) {
                    return
                }
                // 此处避免限流带宽大小比每次申请还少的情况下，每次都被限流
                val realPermitOnce = when {
                    limitPerSecond < permitsOnce -> limitPerSecond
                    permitsOnce < bytes -> bytes.toLong()
                    else -> permitsOnce
                }
                val leftLength = rangeLength!! - bytesRead
                val permits = if (leftLength >= 0) {
                    leftLength.coerceAtMost(realPermitOnce)
                } else {
                    // 当剩余文件大小小于0时，说明文件大小不正确，无法确认剩余多少
                    realPermitOnce
                }
                applyNum += acquireOrWait(permits, bytes.toLong())
            }
        }
    }

    /**
     * 获取对应资源，超过则等待继续重试
     *
     * @param permits 需要申请的许可数量
     * @param bytes 实际需要读取的字节数
     * @param compositeRequest 是否为组合请求（需要多次申请才能满足一次读写操作）
     * @return Long 实际获取到的许可数量
     */
    private fun acquireOrWait(permits: Long, bytes: Long, compositeRequest: Boolean = false): Long {
        var flag = false
        val startTime = System.currentTimeMillis()
        var failedNum = 0
        var acquirePermits = permits
        var alreadyAcquirePermits = 0L

        while (!flag) {
            // 当限制小于读取大小时，会进入死循环，增加等待轮次，如果等待达到时间上限，则放通一次避免连接断开
            if (System.currentTimeMillis() - startTime > rateCheckContext.timeout) {
                return permits.coerceAtMost(bytes)
            }
            try {
                flag = rateCheckContext.rateLimiter.tryAcquire(acquirePermits)
            } catch (ignore: AcquireLockFailedException) {
                return permits
            }
            if (!flag) {
                failedNum++
                val type = selectRateLimitStrategy()
                val tryAcquirePermits = when (type) {
                    TYPE_A -> acquirePermits
                    else -> rateCheckContext.minPermits
                }
                // 根据文件大小/已传输数量去生成下次申请许可数量
                acquirePermits = calculatePermitsOnce(permits, tryAcquirePermits, type)
                try {
                    Thread.sleep(rateCheckContext.latency * failedNum)
                } catch (ignore: InterruptedException) {
                }
                continue
            }
            alreadyAcquirePermits += acquirePermits
            // 判断是否需要继续获取许可
            flag = when {
                compositeRequest -> alreadyAcquirePermits >= bytes
                // 当发生限流时，降低预申请数量，只要达到一次读写要求即可
                failedNum > 0 -> alreadyAcquirePermits >= permits.coerceAtMost(bytes)
                else -> alreadyAcquirePermits >= permits
            }
        }
        return alreadyAcquirePermits
    }

    /**
     * 计算每次申请的许可数量
     * @param realAcquirePermits 实际需要申请的许可数量
     * @param tryAcquirePermits 当前尝试申请的许可数量
     * @param type 限速类型(A/B)
     * @return 计算后的许可数量
     */
    private fun calculatePermitsOnce(realAcquirePermits: Long, tryAcquirePermits: Long, type: String): Long {
        return when (type) {
            // 限速方式A：每次申请容量减半
            TYPE_A -> {
                if (tryAcquirePermits <= rateCheckContext.minPermits * 2) {
                    rateCheckContext.minPermits // 不能小于最小许可数量
                } else {
                    tryAcquirePermits / 2
                }
            }
            // 限速方式B：如指数增长(1K->2K->4K...)
            TYPE_B -> {
                if (tryAcquirePermits <= rateCheckContext.minPermits) {
                    rateCheckContext.minPermits // 从最小许可数量开始
                } else {
                    tryAcquirePermits * 2
                }
            }
            else -> rateCheckContext.minPermits // 默认返回最小许可数量
        }.coerceAtMost(realAcquirePermits) // 不能超过总限速带宽
    }

    /**
     * 根据文件传输进度智能选择限速策略
     * @param transferredBytes 已传输字节数
     * @param totalBytes 总字节数
     * @return 限速类型(A/B)
     */
    private fun selectRateLimitStrategy(): String {
        if (rateCheckContext.rangeLength == null || rateCheckContext.rangeLength!! <= 0) return TYPE_A
        val transferredBytesThreshold = (rateCheckContext.rangeLength!! * rateCheckContext.progressThreshold).toLong()
        return when {
            // 小文件或传输进度超过阈值时使用策略A
            rateCheckContext.rangeLength!! <= rateCheckContext.smallFileThreshold ||
                bytesRead >= transferredBytesThreshold -> TYPE_A
            // 其他情况使用策略B
            else -> TYPE_B
        }
    }

    companion object {
        private const val TYPE_A = "A"
        private const val TYPE_B = "B"
    }
}
