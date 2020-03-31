package com.tencent.bkrepo.replication.stream

import com.tencent.bkrepo.common.stream.event.IEvent
import com.tencent.bkrepo.common.stream.sink.EventSink
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@EnableBinding(EventSink::class)
class StreamConsumer(
    private val publisher: ApplicationEventPublisher
) {

    @StreamListener(EventSink.INPUT)
    fun process(event: IEvent) {
        logger.info("Receive [$event] from stream success.")
        publisher.publishEvent(event)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamListener::class.java)
    }
}
