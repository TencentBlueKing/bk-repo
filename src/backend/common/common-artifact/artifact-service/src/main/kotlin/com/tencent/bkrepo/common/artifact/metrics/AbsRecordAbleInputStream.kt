package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import java.io.InputStream
import java.time.Duration

abstract class AbsRecordAbleInputStream(delegate: InputStream) : DelegateInputStream(delegate) {
    override fun read(): Int {
        executeAndMeasureTime { super.read() }.apply {
            val (read, cost) = this
            if (read >= 0) {
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
    protected abstract fun recordQuiet(size: Int, elapse: Duration)
}
