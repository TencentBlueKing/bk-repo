package com.tencent.bkrepo.media.stream

/**
 * 媒体流
 * */
interface Stream {
    /**
     * 开发发布，执行后，流可以发布数据
     * */
    fun startPublish()

    /**
     * 启动流
     * */
    fun start()

    /**
     * 停止流
     * */
    fun stop()

    /**
     * 关闭流
     * */
    fun close()
}
