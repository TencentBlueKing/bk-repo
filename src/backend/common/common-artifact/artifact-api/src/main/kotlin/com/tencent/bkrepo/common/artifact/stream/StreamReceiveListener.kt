package com.tencent.bkrepo.common.artifact.stream

interface StreamReceiveListener {
    fun data(buffer: ByteArray, offset: Int, length: Int)
    fun finished()
}
