package com.tencent.bkrepo.media.stream

/**
 * 录制监听器
 * */
interface RecordingListener : StreamListener {

    /**
     * 初始化
     * @param name 流名字
     * */
    fun init(name: String)

    /**
     * 开始录制
     * */
    fun start()

    /**
     * 停止录制
     * */
    fun stop()

    /**
     * 是否正在录制
     * */
    fun isRecording(): Boolean
}
