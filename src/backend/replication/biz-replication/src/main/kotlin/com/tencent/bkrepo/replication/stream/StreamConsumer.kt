package com.tencent.bkrepo.replication.stream

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.sink.MessageSink
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@EnableBinding(MessageSink::class)
class StreamConsumer(
    private val publisher: ApplicationEventPublisher
) {

    @StreamListener(MessageSink.INPUT)
    fun process(message: IMessage) {
        logger.info("Receive [$message] from stream success.")
        publisher.publishEvent(message)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamListener::class.java)
    }
}
