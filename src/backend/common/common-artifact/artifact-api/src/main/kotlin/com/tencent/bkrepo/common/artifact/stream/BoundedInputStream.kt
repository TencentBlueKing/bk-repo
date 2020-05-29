package com.tencent.bkrepo.common.artifact.stream

import java.io.File
import java.io.InputStream
import kotlin.math.min

class BoundedInputStream(
    private val source: InputStream,
    limit: Long
) : InputStream() {

    private var pos: Long = 0
    private val length: Long = limit
    private var isPropagateClose: Boolean = true

    init {
        require(length >= 0) { "Limit value must greater than 0." }
    }

    override fun read(): Int {
        if (pos >= length) {
            return EOF
        }
        val result = source.read()
        pos++
        return result
    }

    override fun read(b: ByteArray): Int {
        return this.read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= length) {
            return EOF
        }
        val maxRead = min(len, (length - pos).toInt())
        val bytesRead = source.read(b, off, maxRead)
        if (bytesRead == EOF) {
            return EOF
        }
        pos += bytesRead
        return bytesRead
    }

    override fun skip(n: Long): Long {
        val toSkip = min(n, (length - pos))
        val skippedBytes = source.skip(toSkip)
        pos += skippedBytes
        return skippedBytes
    }

    override fun available(): Int {
        return (length - pos).toInt()
    }

    override fun close() {
        if (isPropagateClose) {
            source.close()
        }
    }

    @Synchronized
    override fun reset() {
        source.reset()
    }

    @Synchronized
    override fun mark(readlimit: Int) {
        source.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return source.markSupported()
    }

    companion object {
        private const val EOF = -1
    }
}

fun InputStream.bound(range: Range): InputStream {
    return if (range.isPartialContent()) {
        BoundedInputStream(this, range.length)
    } else this
}

fun File.bound(range: Range): InputStream {
    return if (range.isPartialContent()) {
        this.inputStream().apply { skip(range.start) }.bound(range)
    } else {
        this.inputStream()
    }
}
