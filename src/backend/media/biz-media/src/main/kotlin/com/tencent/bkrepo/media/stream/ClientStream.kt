package com.tencent.bkrepo.media.stream

import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 客户端流
 * */
class ClientStream(
    val name: String,
    val id: String = name,
    private val maxFileSize: Long,
    val recordingListener: RecordingListener? = null,
) : Stream {

    private var startTime: Long = -1
    private var bytesReceived = 0
    var closed = AtomicBoolean(false)
    var listeners: MutableList<StreamAwareHandler> = mutableListOf()

    override fun startPublish() {
        listeners.forEach { it.streamPublishStart(this) }
    }

    override fun start() {
        startTime = System.currentTimeMillis()
    }

    override fun stop() {
        close()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recordingListener?.stop()
            listeners.forEach { it.streamStop(this) }
        }
    }

    /**
     * 分发来自客户端的流数据
     * @param packet 数据包
     * */
    fun dispatch(packet: StreamPacket) {
        if (!closed.get()) {
            bytesReceived += packet.getData().size
            if (bytesReceived > maxFileSize) {
                stop()
                throw IllegalStateException("except max record file size")
            }
            recordingListener?.packetReceived(packet)
        }
    }

    fun isRecording(): Boolean {
        return recordingListener != null && recordingListener.isRecording()
    }

    fun saveAs() {
        if (recordingListener != null) {
            recordingListener.init(name)
            recordingListener.start()
        }
    }
}
