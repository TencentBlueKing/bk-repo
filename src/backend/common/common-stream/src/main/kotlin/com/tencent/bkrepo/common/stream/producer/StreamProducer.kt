package com.tencent.bkrepo.common.stream.producer

import com.tencent.bkrepo.common.stream.message.IMessage
import com.tencent.bkrepo.common.stream.source.MessageSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.integration.support.MessageBuilder
import org.springframework.stereotype.Component

@Component
@EnableBinding(MessageSource::class)
class StreamProducer {

    @Autowired
    private lateinit var messageSource: MessageSource

    fun sendMessage(message: IMessage) {
        messageSource.output().send(MessageBuilder.withPayload(message).build())
        logger.info("Send message[$message] to stream success.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamProducer::class.java)
    }
}
