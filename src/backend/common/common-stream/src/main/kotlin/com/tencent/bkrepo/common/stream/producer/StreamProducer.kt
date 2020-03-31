package com.tencent.bkrepo.common.stream.producer

import com.tencent.bkrepo.common.stream.event.IEvent
import com.tencent.bkrepo.common.stream.sink.EventSink
import com.tencent.bkrepo.common.stream.source.EventSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.integration.support.MessageBuilder
import org.springframework.stereotype.Component

@Component
@EnableBinding(EventSource::class, EventSink::class)
class StreamProducer {

    @Autowired
    private lateinit var eventSource: EventSource

    fun sendEvent(event: IEvent) {
        eventSource.output().send(MessageBuilder.withPayload(event).build())
        logger.info("Send [$event] to stream success.")
    }

    @StreamListener(EventSink.INPUT)
    fun handle(message: String?) {
        println(String.format("Received: %s", message))
    }
    companion object {
        private val logger = LoggerFactory.getLogger(StreamProducer::class.java)
    }
}
