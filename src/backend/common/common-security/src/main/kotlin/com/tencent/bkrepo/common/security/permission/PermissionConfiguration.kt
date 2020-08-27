package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.security.manager.PermissionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    PermissionAspect::class,
    PrincipalAspect::class
)
class PermissionConfiguration {

    @Bean
    @ConditionalOnMissingBean(PermissionCheckHandler::class)
    fun permissionCheckHandler(permissionManager: PermissionManager): PermissionCheckHandler {
        return DefaultPermissionCheckHandler(permissionManager)
    }
}
