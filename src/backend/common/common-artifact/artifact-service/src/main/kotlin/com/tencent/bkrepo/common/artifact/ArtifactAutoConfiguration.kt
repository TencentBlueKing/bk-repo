package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.ClientAuthInterceptor
import com.tencent.bkrepo.common.artifact.auth.DefaultClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.permission.DefaultPermissionCheckHandler
import com.tencent.bkrepo.common.artifact.permission.PermissionAspect
import com.tencent.bkrepo.common.artifact.permission.PermissionCheckHandler
import com.tencent.bkrepo.common.artifact.resolve.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.ArtifactInfoMethodArgumentResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
// @Import(ResolverScannerRegistrar::class)
@ConditionalOnWebApplication
class ArtifactAutoConfiguration {

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(artifactInfoMethodArgumentResolver())
                resolvers.add(ArtifactFileMethodArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                val clientAuthConfig = artifactConfiguration.getClientAuthConfig()
                registry.addInterceptor(clientAuthInterceptor())
                    .addPathPatterns(clientAuthConfig.pathPatterns)
                    .excludePathPatterns(clientAuthConfig.excludePatterns)
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    fun artifactInfoMethodArgumentResolver() = ArtifactInfoMethodArgumentResolver()

    @Bean
    fun permissionAspect() = PermissionAspect()

    @Bean
    fun clientAuthInterceptor() = ClientAuthInterceptor()

    @Bean
    @ConditionalOnMissingBean(ClientAuthHandler::class)
    fun clientAuthHandler(): ClientAuthHandler = DefaultClientAuthHandler()

    @Bean
    @ConditionalOnMissingBean(PermissionCheckHandler::class)
    fun permissionCheckHandler(): PermissionCheckHandler = DefaultPermissionCheckHandler()
}
