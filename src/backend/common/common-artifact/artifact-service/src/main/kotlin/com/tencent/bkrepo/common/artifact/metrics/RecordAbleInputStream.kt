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

package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * 可记录的输入流
 * */
class RecordAbleInputStream(private val delegate: ArtifactInputStream) :
    DelegateInputStream(delegate) {
    private var trafficHandler: TrafficHandler? = null

    override fun read(): Int {
        executeAndMeasureTime { super.read() }.apply {
            val (read, cost) = this
            if (read > 0) {
                recordQuiet(1, cost)
            }
            return read
        }
    }

    override fun read(byteArray: ByteArray): Int {
        executeAndMeasureTime { super.read(byteArray) }.apply {
            val (read, cost) = this
            if (read > 0) {
                recordQuiet(read, cost)
            }
            return read
        }
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        executeAndMeasureTime { super.read(byteArray, off, len) }.apply {
            val (read, cost) = this
            if (read > 0) {
                recordQuiet(read, cost)
            }
            return read
        }
    }

    /**
     * 静默采集metrics
     * */
    private fun recordQuiet(size: Int, elapse: Duration) {
        try {
            trafficHandler ?: let {
                trafficHandler = TrafficHandler(
                    ArtifactMetrics.getDownloadingCounters(delegate),
                    ArtifactMetrics.getDownloadingTimer(delegate)
                )
            }
            trafficHandler?.record(size, elapse)
        } catch (e: Exception) {
            logger.error("Record download metrics error", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecordAbleInputStream::class.java)
    }
}
