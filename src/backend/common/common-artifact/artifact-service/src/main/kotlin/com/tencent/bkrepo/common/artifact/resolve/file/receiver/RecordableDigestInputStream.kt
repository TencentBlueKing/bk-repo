package com.tencent.bkrepo.common.artifact.resolve.file.receiver

import com.tencent.bkrepo.common.artifact.metrics.AbsRecordAbleInputStream
import com.tencent.bkrepo.common.artifact.metrics.TrafficHandler
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Duration

class RecordableDigestInputStream(
    delegate: InputStream,
    private val trafficHandler: TrafficHandler,
    private val digestListener: DigestCalculateListener
) : AbsRecordAbleInputStream(delegate) {

    override fun read(): Int {
        val data = super.read()
        if (data >= 0) {
            digestListener.data(data)
        }
        return data
    }

    override fun read(byteArray: ByteArray): Int {
        val read = super.read(byteArray)
        if (read > 0) {
            digestListener.data(byteArray, 0, read)
        }
        return read
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        val read = super.read(byteArray, off, len)
        if (read > 0) {
            digestListener.data(byteArray, off, read)
        }
        return read
    }

    override fun recordQuiet(size: Int, elapse: Duration) {
        try {
            trafficHandler.record(size, elapse)
        } catch (e: Exception) {
            logger.error("Record download metrics error", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecordableDigestInputStream::class.java)
    }
}
