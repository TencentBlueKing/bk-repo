package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownHook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 流管理器
 * 负责流的监控与管理
 * */
@Service
class StreamManger : StreamAwareHandler {
    val streams: MutableMap<String, ClientStream> = mutableMapOf()

    init {
        ServiceShutdownHook.add { this.close() }
    }

    override fun streamPublishStart(stream: ClientStream) {
        addStream(stream)
    }

    override fun streamStop(stream: ClientStream) {
        deleteStream(stream)
    }

    private fun addStream(stream: ClientStream): ClientStream {
        val id = stream.id
        if (streams.putIfAbsent(id, stream) != null) {
            throw IllegalStateException("Stream $id existed")
        }
        logger.info("Add stream $id")
        return stream
    }

    private fun deleteStream(stream: ClientStream) {
        val id = stream.id
        if (streams[id] != null) {
            if (!stream.closed.get()) {
                stream.stop()
            }
            streams.remove(id)
            logger.info("Delete stream $id")
        }
    }

    fun close() {
        logger.info("Closing stream manager")
        streams.map { it.value }.forEach {
            logger.info("Stopping stream ${it.id}")
            it.stop()
        }
        streams.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamManger::class.java)
    }
}
