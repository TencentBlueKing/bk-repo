package com.tencent.bkrepo.common.artifact.stream

import java.io.InputStream

class ZeroInputStream(private var size: Long = -1) : InputStream() {
    private var read = 0

    override fun read(): Int {
        if (size in 0..read) return -1
        read++
        return 0
    }

    override fun available(): Int {
        return if (size >= 0) size.toInt() - read else Int.MAX_VALUE
    }
}
