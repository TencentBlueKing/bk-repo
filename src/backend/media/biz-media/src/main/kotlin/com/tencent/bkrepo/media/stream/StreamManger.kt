package com.tencent.bkrepo.media.stream

import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownHook
import com.tencent.bkrepo.media.service.StreamService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/**
 * 流管理器
 * 负责流的监控与管理
 * */
@Service
class StreamManger(
    private val streamServiceProvider: ObjectProvider<StreamService>,
) : StreamAwareHandler {
    val streams: MutableMap<String, ClientStream> = mutableMapOf()

    init {
        ServiceShutdownHook.add { this.close(System.currentTimeMillis()) }
    }

    override fun streamPublishStart(stream: ClientStream) {
        addStream(stream)
    }

    override fun streamStop(stream: ClientStream, endTime: Long) {
        deleteStream(stream, endTime)
    }

    private fun addStream(stream: ClientStream): ClientStream {
        val id = stream.id
        if (streams.putIfAbsent(id, stream) != null) {
            throw IllegalStateException("Stream $id existed")
        }
        syncActiveStream(id) { it.saveActiveStream(id) }
        logger.info("Add stream $id")
        return stream
    }

    private fun deleteStream(stream: ClientStream, endTime: Long) {
        val id = stream.id
        if (streams[id] != null) {
            if (!stream.closed.get()) {
                stream.stop(endTime)
            }
            streams.remove(id)
            syncActiveStream(id) { it.deleteActiveStream(id) }
            logger.info("Delete stream $id")
        }
    }

    private fun syncActiveStream(streamId: String, action: (StreamService) -> Unit) {
        runCatching {
            streamServiceProvider.getIfAvailable()?.let { action(it) }
        }.onFailure {
            logger.warn("Sync active stream failed: streamId=$streamId", it)
        }
    }

    fun close(endTime: Long) {
        logger.info("Closing stream manager")
        streams.map { it.value }.forEach {
            logger.info("Stopping stream ${it.id}")
            it.stop(endTime)
        }
        streams.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamManger::class.java)
    }
}
