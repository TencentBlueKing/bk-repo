/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.api.net.speedtest

import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * 流量计数器
 * */
class Counter(
    val maxSendSize: Long,
    val totalBytes: AtomicLong
) : InputStream() {
    private val blob: ByteArray = Random.nextBytes(MB)
    private val inputStream = ByteArrayInputStream(blob)
    private var pos = 0
    var total: Long = 0
    private var start: Long = 0

    fun start() {
        start = System.currentTimeMillis()
    }

    fun avgBytes(): Long {
        return total / (System.currentTimeMillis() - start) * 1000
    }

    override fun read(): Int {
        throw MethodNotAllowedException()
    }

    override fun read(b: ByteArray): Int {
        if (total == maxSendSize) {
            return -1
        }
        val len = (maxSendSize - total).coerceAtMost(b.size.toLong())
        val read = inputStream.read(b, 0, len.toInt())
        total += read
        pos += read
        totalBytes.addAndGet(read.toLong())
        if (pos == blob.size) {
            resetReader()
        }
        return read
    }

    private fun resetReader() {
        pos = 0
        inputStream.reset()
    }

    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
    }
}
