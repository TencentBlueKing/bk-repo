package com.tencent.bkrepo.common.stream.binder.memory.config

import com.tencent.bkrepo.common.stream.binder.memory.MemoryMessageChannelBinder
import com.tencent.bkrepo.common.stream.binder.memory.MemoryProvisioningProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.stream.binder.Binder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnMissingBean(Binder::class)
@EnableConfigurationProperties(
    MemoryBinderConfigurationProperties::class,
    MemoryExtendedBindingProperties::class
)
class MemoryBinderConfiguration {

    @Bean
    fun memoryMessageChannelBinder(
        configurationProperties: MemoryBinderConfigurationProperties,
        extendedProperties: MemoryExtendedBindingProperties
    ): MemoryMessageChannelBinder {
        return MemoryMessageChannelBinder(
            configurationProperties,
            extendedProperties,
            MemoryProvisioningProvider()
        )
    }
}
