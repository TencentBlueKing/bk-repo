package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.auth.ClientAuthInterceptor
import com.tencent.bkrepo.common.artifact.resolve.ArtifactInfoMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.ArtifactCoordinateResolver
import com.tencent.bkrepo.common.artifact.resolve.DefaultArtifactCoordinateResolver
import com.tencent.bkrepo.common.artifact.resolve.ArtifactFileMethodArgumentResolver
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
@ConditionalOnWebApplication
class ArtifactAutoConfiguration {

    @Autowired
    private lateinit var artifactCoordinateResolver: ArtifactCoordinateResolver

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(ArtifactInfoMethodArgumentResolver(artifactCoordinateResolver))
                resolvers.add(ArtifactFileMethodArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(clientAuthInterceptor())
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ArtifactCoordinateResolver::class)
    fun artifactPathResolver(): ArtifactCoordinateResolver = DefaultArtifactCoordinateResolver()

    @Bean
    fun clientAuthInterceptor(): ClientAuthInterceptor = ClientAuthInterceptor()
}