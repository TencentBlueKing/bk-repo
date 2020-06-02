package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileCleanInterceptor
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.UploadProperties
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.ArtifactFileMapMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.stream.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoMethodArgumentResolver
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Import(ArtifactFileFactory::class)
@EnableConfigurationProperties(UploadProperties::class)
class ResolverConfiguration {

    @Bean
    fun artifactArgumentResolveConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(ArtifactInfoMethodArgumentResolver())
                resolvers.add(ArtifactFileMethodArgumentResolver())
                resolvers.add(ArtifactFileMapMethodArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(ArtifactFileCleanInterceptor())
                super.addInterceptors(registry)
            }
        }
    }
}
