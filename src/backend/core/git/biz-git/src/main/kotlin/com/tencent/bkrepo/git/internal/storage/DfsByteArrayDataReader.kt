package com.tencent.bkrepo.git.internal.storage

import java.nio.ByteBuffer

class DfsByteArrayDataReader(val data: ByteArray) : DfsDataReader {
    private val length = data.size
    override fun read(pos: Long, dst: ByteBuffer): Int {
        val size = dst.remaining().coerceAtMost((length - pos).toInt())
        if (size == 0) {
            return -1
        }
        dst.put(data, pos.toInt(), size)
        return size
    }

    override fun size(): Long {
        return length.toLong()
    }

    override fun close() {
        // array reader nothing to close
    }
}
