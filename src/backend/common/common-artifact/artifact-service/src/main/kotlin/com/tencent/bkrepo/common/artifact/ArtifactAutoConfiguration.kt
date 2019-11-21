package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.resolve.ArtifactInfoMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.ArtifactPathResolver
import com.tencent.bkrepo.common.artifact.resolve.DefaultArtifactPathResolver
import com.tencent.bkrepo.common.artifact.resolve.ArtifactDataMethodArgumentResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
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
    lateinit var artifactPathResolver: ArtifactPathResolver

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(ArtifactInfoMethodArgumentResolver(artifactPathResolver))
                resolvers.add(ArtifactDataMethodArgumentResolver())
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(ArtifactPathResolver::class)
    fun artifactPathResolver(): ArtifactPathResolver = DefaultArtifactPathResolver()
}