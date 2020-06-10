package com.tencent.bkrepo.common.artifact.stream

import com.google.common.util.concurrent.RateLimiter
import java.io.InputStream

class RateLimitInputStream (
    private val source: InputStream,
    rate: Long
): InputStream() {
    private val rateLimiter: RateLimiter? = if (rate > 0) RateLimiter.create(rate.toDouble()) else null

    override fun skip(n: Long): Long {
        return source.skip(n)
    }

    override fun available(): Int {
        return source.available()
    }

    override fun reset() {
        source.reset()
    }

    override fun close() {
        source.close()
    }

    override fun mark(readlimit: Int) {
        source.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return source.markSupported()
    }

    override fun read(): Int {
        rateLimiter?.acquire()
        return source.read()
    }

    override fun read(b: ByteArray): Int {
        rateLimiter?.acquire(b.size)
        return source.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        rateLimiter?.acquire(len)
        return source.read(b, off, len)
    }
}