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

    fun addStream(stream: ClientStream): ClientStream {
        val name = stream.name
        if (streams.putIfAbsent(name, stream) != null) {
            throw IllegalStateException("Stream $name existed")
        }
        logger.info("Add stream $name")
        return stream
    }

    fun deleteStream(stream: ClientStream) {
        val name = stream.name
        if (streams[name] != null) {
            if (!stream.closed.get()) {
                stream.stop()
            }
            streams.remove(name)
            logger.info("Delete stream $name")
        }
    }

    fun close() {
        logger.info("Closing stream manager")
        streams.forEach {
            logger.info("Stopping stream ${it.key}")
            it.value.stop()
        }
        streams.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamManger::class.java)
    }
}
