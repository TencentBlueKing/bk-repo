package com.tencent.bkrepo.common.artifact.permission

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PermissionConfiguration {

    @Bean
    fun permissionService() = PermissionService()

    @Bean
    fun permissionAspect() = PermissionAspect()

    @Bean
    fun principalAspect() = PrincipalAspect()

    @Bean
    @ConditionalOnMissingBean(PermissionCheckHandler::class)
    fun permissionCheckHandler(): PermissionCheckHandler = DefaultPermissionCheckHandler()
}
