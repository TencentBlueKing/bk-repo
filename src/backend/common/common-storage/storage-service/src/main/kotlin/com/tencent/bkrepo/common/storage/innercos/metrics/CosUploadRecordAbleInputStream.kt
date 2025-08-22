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

package com.tencent.bkrepo.common.storage.innercos.metrics

import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import io.micrometer.core.instrument.Counter
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * 记录cos上传流
 * */
class CosUploadRecordAbleInputStream(private val delegate: InputStream) :
    DelegateInputStream(delegate) {
    private var uploadCounter: Counter? = null

    override fun read(): Int {
        val read = super.read()
        if (read > 0) {
            recordQuiet(1)
        }
        return read
    }

    override fun read(byteArray: ByteArray): Int {
        val read = super.read(byteArray)
        if (read > 0) {
            recordQuiet(read)
        }
        return read
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        val read = super.read(byteArray, off, len)
        if (read > 0) {
            recordQuiet(read)
        }
        return read
    }

    /**
     * 静默采集metrics
     * */
    private fun recordQuiet(size: Int) {
        try {
            uploadCounter ?: let {
                uploadCounter = CosUploadMetrics.getUploadingCounter()
            }
            uploadCounter?.increment(size.toDouble())
        } catch (e: Exception) {
            logger.error("Record cos upload metrics error", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CosUploadRecordAbleInputStream::class.java)
    }
}
