package com.tencent.bkrepo.common.stream.binder.memory.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.stream.binder.AbstractExtendedBindingProperties
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider

@ConfigurationProperties("spring.cloud.stream.memory")
class MemoryExtendedBindingProperties :
    AbstractExtendedBindingProperties<MemoryConsumerProperties, MemoryProducerProperties, MemoryBindingProperties>() {

    companion object {
        const val DEFAULTS_PREFIX = "spring.cloud.stream.memory.default"
    }

    override fun getDefaultsPrefix(): String = DEFAULTS_PREFIX

    override fun getExtendedPropertiesEntryClass(): Class<out BinderSpecificPropertiesProvider> {
        return MemoryBindingProperties::class.java
    }
}
