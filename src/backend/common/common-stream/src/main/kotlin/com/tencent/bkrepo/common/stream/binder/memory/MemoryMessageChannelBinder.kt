package com.tencent.bkrepo.common.stream.binder.memory

import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryBinderConfigurationProperties
import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryConsumerProperties
import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryExtendedBindingProperties
import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryProducerProperties
import org.springframework.cloud.stream.binder.AbstractMessageChannelBinder
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties
import org.springframework.cloud.stream.binder.ExtendedProducerProperties
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder
import org.springframework.cloud.stream.provisioning.ConsumerDestination
import org.springframework.cloud.stream.provisioning.ProducerDestination
import org.springframework.integration.core.MessageProducer
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler

class MemoryMessageChannelBinder(
    private val configurationProperties: MemoryBinderConfigurationProperties,
    private val extendedBindingProperties: MemoryExtendedBindingProperties,
    provisioningProvider: MemoryProvisioningProvider
) : AbstractMessageChannelBinder<
    ExtendedConsumerProperties<MemoryConsumerProperties>,
    ExtendedProducerProperties<MemoryProducerProperties>,
    MemoryProvisioningProvider>(arrayOf(), provisioningProvider),
    ExtendedPropertiesBinder<MessageChannel, MemoryConsumerProperties, MemoryProducerProperties> {

    override fun getExtendedConsumerProperties(channelName: String): MemoryConsumerProperties {
        return this.extendedBindingProperties.getExtendedConsumerProperties(channelName)
    }

    override fun getExtendedProducerProperties(channelName: String): MemoryProducerProperties {
        return this.extendedBindingProperties.getExtendedProducerProperties(channelName)
    }

    override fun getDefaultsPrefix(): String {
        return this.extendedBindingProperties.defaultsPrefix
    }

    override fun getExtendedPropertiesEntryClass(): Class<out BinderSpecificPropertiesProvider> {
        return this.extendedBindingProperties.extendedPropertiesEntryClass
    }

    override fun createConsumerEndpoint(
        destination: ConsumerDestination,
        group: String?,
        properties: ExtendedConsumerProperties<MemoryConsumerProperties>?
    ): MessageProducer {
        return MemoryConsumerEndpoint(
            destination,
            group.orEmpty(),
            properties ?: ExtendedConsumerProperties(MemoryConsumerProperties())
        )
    }

    override fun createProducerMessageHandler(
        destination: ProducerDestination,
        producerProperties: ExtendedProducerProperties<MemoryProducerProperties>?,
        errorChannel: MessageChannel?
    ): MessageHandler {
        return MemoryMessageHandler(
            configurationProperties,
            destination,
            producerProperties ?: ExtendedProducerProperties(MemoryProducerProperties())
        )
    }
}
