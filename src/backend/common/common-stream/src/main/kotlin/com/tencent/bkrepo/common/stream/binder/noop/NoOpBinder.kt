package com.tencent.bkrepo.common.stream.binder.noop

import org.springframework.cloud.stream.binder.Binder
import org.springframework.cloud.stream.binder.Binding
import org.springframework.cloud.stream.binder.ConsumerProperties
import org.springframework.cloud.stream.binder.ProducerProperties
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.SubscribableChannel

class NoOpBinder : Binder<MessageChannel, ConsumerProperties, ProducerProperties> {
    override fun bindProducer(
        name: String,
        outboundBindTarget: MessageChannel,
        producerProperties: ProducerProperties
    ): Binding<MessageChannel> {
        (outboundBindTarget as SubscribableChannel).subscribe { }
        return Binding<MessageChannel> {
            fun isInput() = true
        }
    }

    override fun bindConsumer(
        name: String,
        group: String?,
        inboundBindTarget: MessageChannel,
        consumerProperties: ConsumerProperties
    ): Binding<MessageChannel> {
        return Binding<MessageChannel> {
            fun isInput() = false
        }
    }
}
