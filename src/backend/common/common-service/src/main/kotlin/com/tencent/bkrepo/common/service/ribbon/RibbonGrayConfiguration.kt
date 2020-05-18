package com.tencent.bkrepo.common.service.ribbon

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.netflix.ribbon.RibbonClientConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Configuration
@ConditionalOnProperty(value = ["ribbon.gray.enabled"])
@AutoConfigureBefore(RibbonClientConfiguration::class)
@EnableConfigurationProperties(RibbonGrayProperties::class)
class RibbonGrayConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun grayMetadataAwareRule() = GrayMetadataAwareRule()
}
