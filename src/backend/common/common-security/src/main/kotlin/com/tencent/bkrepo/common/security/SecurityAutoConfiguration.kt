/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.security

import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.security.actuator.ActuatorAuthConfiguration
import com.tencent.bkrepo.common.security.crypto.CryptoConfiguration
import com.tencent.bkrepo.common.security.exception.SecurityExceptionHandler
import com.tencent.bkrepo.common.security.http.HttpAuthConfiguration
import com.tencent.bkrepo.common.security.http.core.HttpAuthProperties
import com.tencent.bkrepo.common.security.interceptor.devx.DevXAccessInterceptor
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.manager.edge.EdgePermissionManager
import com.tencent.bkrepo.common.security.permission.PermissionConfiguration
import com.tencent.bkrepo.common.security.service.ServiceAuthConfiguration
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConditionalOnWebApplication
@Import(
    SecurityExceptionHandler::class,
    AuthenticationManager::class,
    PermissionConfiguration::class,
    HttpAuthConfiguration::class,
    ServiceAuthConfiguration::class,
    ActuatorAuthConfiguration::class,
    CryptoConfiguration::class
)
@EnableConfigurationProperties(DevXProperties::class)
class SecurityAutoConfiguration {

    @Bean
    fun permissionManager(
        repositoryClient: RepositoryClient,
        permissionResource: ServicePermissionClient,
        externalPermissionResource: ServiceExternalPermissionClient,
        userResource: ServiceUserClient,
        nodeClient: NodeClient,
        clusterProperties: ClusterProperties,
        httpAuthProperties: HttpAuthProperties
    ): PermissionManager {
        return if (clusterProperties.role == ClusterNodeType.EDGE
            && clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
        ) {
            EdgePermissionManager(
                repositoryClient = repositoryClient,
                permissionResource = permissionResource,
                externalPermissionResource = externalPermissionResource,
                userResource = userResource,
                nodeClient = nodeClient,
                clusterProperties = clusterProperties,
                httpAuthProperties = httpAuthProperties
            )
        } else {
            PermissionManager(
                repositoryClient = repositoryClient,
                permissionResource = permissionResource,
                externalPermissionResource = externalPermissionResource,
                userResource = userResource,
                nodeClient = nodeClient,
                httpAuthProperties = httpAuthProperties
            )
        }
    }

    @Bean
    @ConditionalOnProperty(value = ["devx.enabled"])
    fun devXAccessInterceptorConfigure(
        properties: DevXProperties,
        devXAccessInterceptor: DevXAccessInterceptor,
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                // 不应用到服务间调用
                val registration = registry.addInterceptor(devXAccessInterceptor)
                    // 需要在[httpAuthInterceptor]之后执行才能取得用户信息, 100为随机取值
                    .order(properties.interceptorOrder)
                    .excludePathPatterns("/service/**", "/replica/**")
                if (properties.excludePatterns.isNotEmpty()) {
                    registration.excludePathPatterns(properties.excludePatterns)
                }
                if (properties.includePatterns.isNotEmpty()) {
                    registration.addPathPatterns(properties.includePatterns)
                }
            }
        }
    }

    @Bean
    @ConditionalOnProperty(value = ["devx.enabled"])
    @ConditionalOnMissingBean
    fun devXAccessInterceptor(properties: DevXProperties): DevXAccessInterceptor {
        return DevXAccessInterceptor(properties)
    }
}
