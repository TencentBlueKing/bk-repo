/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.service.util

import org.bouncycastle.util.encoders.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.math.absoluteValue

class PercentageGrayUtilsTest {

    @Test
    fun testHit() {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val processes = Array(availableProcessors) { MockProcess() }
        val total = 1000000
        val percent = 0.2
        val actualCount = IntStream
            .range(0, total)
            .parallel()
            .unordered()
            .mapToObj { processes[(Thread.currentThread().id % availableProcessors).toInt()].generateObjectId() }
            .filter { PercentageGrayUtils.hitByObjectId(it, percent) }
            .count()

        // 期望值与实际命中灰度的数量误差在0.1%内
        val expectedCount = (total * percent)
        val error = (expectedCount - actualCount).absoluteValue / total
        Assertions.assertTrue(error < 0.001)
    }

    /**
     * 模拟创建objectId的进程
     */
    private class MockProcess {
        private val machine: Int
        private val pid: Short
        private val counter = AtomicInteger(SecureRandom().nextInt())
        private val timestampSecond = AtomicInteger((Date().time / 1000).toInt())

        init {
            val secureRandom = SecureRandom()
            machine = secureRandom.nextInt(0x01000000)
            pid = secureRandom.nextInt(0x00008000).toShort()
        }

        /**
         * ObjectId由[timestamp(0-3),machine(4-6),pid(7-8),inc(9-11)]总共12字节组成
         */
        fun generateObjectId(): String {
            val buffer = ByteBuffer.allocate(12)

            val timestamp = timestampSecond.get()
            buffer.put(int3(timestamp))
            buffer.put(int2(timestamp))
            buffer.put(int1(timestamp))
            buffer.put(int0(timestamp))

            buffer.put(int2(machine))
            buffer.put(int1(machine))
            buffer.put(int0(machine))

            buffer.put(short1(pid))
            buffer.put(short0(pid))

            val inc = counter.getAndIncrement().and(LOW_ORDER_THREE_BYTES)
            buffer.put(int2(inc))
            buffer.put(int1(inc))
            buffer.put(int0(inc))

            return Hex.toHexString(buffer.array())
        }
    }

    companion object {
        private const val LOW_ORDER_THREE_BYTES = 0x00ffffff

        private fun int3(x: Int): Byte {
            return (x shr 24).toByte()
        }

        private fun int2(x: Int): Byte {
            return (x shr 16).toByte()
        }

        private fun int1(x: Int): Byte {
            return (x shr 8).toByte()
        }

        private fun int0(x: Int): Byte {
            return x.toByte()
        }

        private fun short1(x: Short): Byte {
            return (x.toInt() shr 8).toByte()
        }

        private fun short0(x: Short): Byte {
            return x.toByte()
        }
    }
}
