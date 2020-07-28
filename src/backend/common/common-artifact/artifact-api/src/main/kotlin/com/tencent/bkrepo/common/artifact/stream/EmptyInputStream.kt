package com.tencent.bkrepo.common.artifact.stream

import java.io.InputStream

class EmptyInputStream : InputStream() {
    override fun available() = 0
    override fun read() = -1
    override fun read(buf: ByteArray) = -1
    override fun read(buf: ByteArray, off: Int, len: Int): Int = -1
    override fun reset() {}
    override fun skip(n: Long): Long {
        return 0L
    }
    override fun close() {}
    override fun mark(readLimit: Int) {}
    override fun markSupported() = true

    companion object {
        val INSTANCE = EmptyInputStream()
    }
}
