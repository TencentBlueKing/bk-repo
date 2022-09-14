package com.tencent.bkrepo.common.artifact.cluster

import com.tencent.bkrepo.common.artifact.interceptor.EdgeNodeUploadInterceptor
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ArtifactClusterConfiguration {
    @Bean
    fun clusterWebMvcConfigurer(clusterProperties: ClusterProperties) = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(EdgeNodeUploadInterceptor(clusterProperties))
            super.addInterceptors(registry)
        }
    }
}
