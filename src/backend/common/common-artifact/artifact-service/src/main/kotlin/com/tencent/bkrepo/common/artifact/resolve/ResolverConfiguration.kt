package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileCleanInterceptor
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.UploadConfigElement
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.ArtifactFileMapMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.stream.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoMethodArgumentResolver
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.MonitorProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Import(ArtifactFileFactory::class)
@EnableConfigurationProperties(MonitorProperties::class)
class ResolverConfiguration {

    @Bean
    fun artifactArgumentResolveConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(artifactInfoMethodArgumentResolver())
                resolvers.add(artifactFileMethodArgumentResolver())
                resolvers.add(artifactFileMapMethodArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(ArtifactFileCleanInterceptor())
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    fun artifactInfoMethodArgumentResolver() = ArtifactInfoMethodArgumentResolver()

    @Bean
    fun artifactFileMethodArgumentResolver() = ArtifactFileMethodArgumentResolver()

    @Bean
    fun artifactFileMapMethodArgumentResolver() = ArtifactFileMapMethodArgumentResolver()

    @Bean
    fun uploadConfigElement(storageProperties: StorageProperties): UploadConfigElement {
        return UploadConfigElement(storageProperties)
    }

    @Bean
    fun storageHealthMonitor(storageProperties: StorageProperties, monitorProperties: MonitorProperties): StorageHealthMonitor {
        return StorageHealthMonitor(storageProperties, monitorProperties)
    }
}
