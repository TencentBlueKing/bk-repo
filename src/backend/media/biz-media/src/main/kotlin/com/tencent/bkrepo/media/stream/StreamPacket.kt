package com.tencent.bkrepo.media.stream

/**
 * 流数据包
 * */
interface StreamPacket {
    /**
     * 包类型
     * */
    fun getDataType(): Byte

    /**
     * 包时间戳
     * */
    fun getTimestamp(): Long

    /**
     * 包数据
     * */
    fun getData(): ByteArray
}
