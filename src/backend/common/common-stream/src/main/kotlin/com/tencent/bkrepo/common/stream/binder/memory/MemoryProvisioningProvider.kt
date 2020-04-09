package com.tencent.bkrepo.common.stream.binder.memory

import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryConsumerProperties
import com.tencent.bkrepo.common.stream.binder.memory.config.MemoryProducerProperties
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties
import org.springframework.cloud.stream.binder.ExtendedProducerProperties
import org.springframework.cloud.stream.provisioning.ConsumerDestination
import org.springframework.cloud.stream.provisioning.ProducerDestination
import org.springframework.cloud.stream.provisioning.ProvisioningProvider

class MemoryProvisioningProvider : ProvisioningProvider<
        ExtendedConsumerProperties<MemoryConsumerProperties>,
        ExtendedProducerProperties<MemoryProducerProperties>> {

    override fun provisionProducerDestination(
        name: String?,
        properties: ExtendedProducerProperties<MemoryProducerProperties>
    ): ProducerDestination = object : ProducerDestination {
        override fun getName() = name.orEmpty()
        override fun getNameForPartition(partition: Int): String = name.orEmpty()
    }

    override fun provisionConsumerDestination(
        name: String?,
        group: String?,
        properties: ExtendedConsumerProperties<MemoryConsumerProperties>?
    ): ConsumerDestination = ConsumerDestination { name.orEmpty() }
}
