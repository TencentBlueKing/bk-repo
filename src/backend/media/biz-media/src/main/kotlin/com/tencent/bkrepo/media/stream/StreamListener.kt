package com.tencent.bkrepo.media.stream

/**
 * 流监听器
 * */
interface StreamListener {
    /**
     * 接收到流数据
     * */
    fun packetReceived(packet: StreamPacket)
}
