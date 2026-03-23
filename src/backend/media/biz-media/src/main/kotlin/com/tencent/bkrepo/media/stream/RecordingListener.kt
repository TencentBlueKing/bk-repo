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
    fun stop(endTime: Long)

    /**
     * 停止录制
     * @param endTime 结束时间
     * @param isComplete 是否正常完成（true=正常结束需合并分块，false=异常断开仅存分块）
     */
    fun stop(endTime: Long, isComplete: Boolean) {
        stop(endTime)
    }

    /**
     * 是否正在录制
     * */
    fun isRecording(): Boolean
}
