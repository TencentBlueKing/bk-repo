package com.tencent.bkrepo.common.stream.binder.memory.config

import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider

class MemoryBindingProperties(
    private var consumer: MemoryConsumerProperties = MemoryConsumerProperties(),
    private var producer: MemoryProducerProperties = MemoryProducerProperties()
) : BinderSpecificPropertiesProvider {

    fun setConsumer(consumerProperties: MemoryConsumerProperties) {
        this.consumer = consumerProperties
    }

    fun setProducer(producerProperties: MemoryProducerProperties) {
        this.producer = producerProperties
    }

    override fun getConsumer(): Any {
        return this.consumer
    }

    override fun getProducer(): Any {
        return this.producer
    }
}
