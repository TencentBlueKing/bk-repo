package com.tencent.bkrepo.preview.config

import com.tencent.bkrepo.preview.service.share.NoopOrgScopeIdMappingService
import com.tencent.bkrepo.preview.service.share.OrgScopeIdMappingService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArtifactShareProperties::class, BKUserCustomProperties::class)
class PreviewShareConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrgScopeIdMappingService::class)
    fun orgScopeIdMappingService() = NoopOrgScopeIdMappingService()
}
