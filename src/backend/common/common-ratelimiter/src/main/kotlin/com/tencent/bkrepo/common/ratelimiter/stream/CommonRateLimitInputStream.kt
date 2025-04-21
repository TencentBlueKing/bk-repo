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
    private val rateCheckContext: RateCheckContext,
) : DelegateInputStream(delegate) {

    private var bytesRead: Long = 0
    private var applyNum: Long = 0

    fun setBytesRead(bytesRead: Long) {
        this.bytesRead = bytesRead
    }

    fun setApplyNum(applyNum: Long) {
        this.applyNum = applyNum
    }

    fun getBytesRead(): Long {
        return bytesRead
    }

    fun getApplyNum(): Long {
        return applyNum
    }

    override fun read(): Int {
        tryAcquireOrWait(1)
        val data = super.read()
        if (data != -1) {
            bytesRead++
        }
        return data
    }

    override fun read(byteArray: ByteArray): Int {
        tryAcquireOrWait(byteArray.size)
        val readLen = super.read(byteArray)
        if (readLen != -1) {
            bytesRead += readLen
        }
        return readLen
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        tryAcquireOrWait(len)
        val readLen = super.read(byteArray, off, len)
        if (readLen != -1) {
            bytesRead += readLen
        }
        return readLen
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
            if ((System.currentTimeMillis() - startTime) > rateCheckContext.timeout) {
                return permits.coerceAtMost(bytes)
            }
            try {
                flag = rateCheckContext.getRateLimiter().tryAcquire(acquirePermits)
            } catch (ignore: AcquireLockFailedException) {
                return permits
            }
            if (!flag) {
                if (rateCheckContext.dryRun) {
                    return permits
                }
                failedNum++
                val type = selectRateLimitStrategy()
                val tryAcquirePermits = getTryAcquirePermits(type, acquirePermits, alreadyAcquirePermits)
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
                compositeRequest -> {
                    acquirePermits = if (failedNum > 0) {
                        (acquirePermits * 2).coerceAtMost(permits)
                    } else {
                        acquirePermits
                    }.coerceAtMost(bytes - alreadyAcquirePermits)
                    alreadyAcquirePermits >= bytes
                }
                // 当发生限流时，降低预申请数量，只要达到一次读写要求即可
                failedNum > 0 -> {
                    // 当发生限速后， 此时申请成功，下一次申请请求翻倍
                    acquirePermits = (acquirePermits * 2).coerceAtMost(
                        permits.coerceAtMost(bytes) - alreadyAcquirePermits
                    )
                    // 默认情况下申请数量会大于单次读取数量，在发生限速后，只需申请大于单次读取数量即可，快速响应
                    alreadyAcquirePermits >= permits.coerceAtMost(bytes)
                }
                else -> alreadyAcquirePermits >= permits
            }
        }
        return alreadyAcquirePermits
    }

    private fun getTryAcquirePermits(type: String, acquirePermits: Long, alreadyAcquirePermits: Long) : Long {
        return when (type) {
            TYPE_A -> acquirePermits
            // 当已经申请通过一定数量后，又失败，避免再次从最小开始，直接从上次减半开始
            TYPE_B -> if (alreadyAcquirePermits == 0L) {
                rateCheckContext.minPermits
            } else {
                acquirePermits / 2
            }
            else -> rateCheckContext.minPermits
        }
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
        const val TYPE_A = "A"
        const val TYPE_B = "B"
    }
}
