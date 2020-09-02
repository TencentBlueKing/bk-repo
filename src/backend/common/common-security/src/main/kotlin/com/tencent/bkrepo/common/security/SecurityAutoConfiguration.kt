package com.tencent.bkrepo.common.security

import com.tencent.bkrepo.common.security.actuator.ActuatorAuthConfiguration
import com.tencent.bkrepo.common.security.exception.SecurityExceptionHandler
import com.tencent.bkrepo.common.security.http.HttpAuthConfiguration
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.PermissionConfiguration
import com.tencent.bkrepo.common.security.service.ServiceAuthConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication
@Import(
    HttpAuthConfiguration::class,
    ServiceAuthConfiguration::class,
    ActuatorAuthConfiguration::class,
    PermissionConfiguration::class,
    SecurityExceptionHandler::class,
    AuthenticationManager::class,
    PermissionManager::class
)
class SecurityAutoConfiguration
