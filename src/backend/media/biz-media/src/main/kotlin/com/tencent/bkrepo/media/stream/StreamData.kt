package com.tencent.bkrepo.media.stream

class StreamData(
    private var data: ByteArray,
    private val type: Byte,
    private var ts: Long,
) : StreamPacket {
    override fun getDataType(): Byte {
        return type
    }

    override fun getTimestamp(): Long {
        return ts
    }

    override fun getData(): ByteArray {
        return data
    }
}