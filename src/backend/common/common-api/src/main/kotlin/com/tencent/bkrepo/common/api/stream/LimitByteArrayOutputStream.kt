package com.tencent.bkrepo.common.api.stream

import java.io.ByteArrayOutputStream

/**
 * 可限制的ByteArrayOutputStream，防止无限扩容，导致内存不足
 * */
class LimitByteArrayOutputStream(private val limit: Long) : ByteArrayOutputStream() {

    override fun write(b: Int) {
        if (super.size() + 1 > limit) {
            throw RuntimeException("content overflow.")
        }
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (super.size() + len > limit) {
            throw RuntimeException("content overflow.")
        }
        super.write(b, off, len)
    }
}
