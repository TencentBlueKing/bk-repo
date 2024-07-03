package com.tencent.bkrepo.media.stream

/**
 * 流处理器
 * */
interface StreamAwareHandler {
    /**
     * 流开始发布
     * */
    fun streamPublishStart(stream: ClientStream)

    /**
     * 流停止
     * */
    fun streamStop(stream: ClientStream)
}
