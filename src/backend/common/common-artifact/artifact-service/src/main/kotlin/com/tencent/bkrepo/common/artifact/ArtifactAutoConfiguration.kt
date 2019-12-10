package com.tencent.bkrepo.common.artifact

import com.tencent.bkrepo.common.artifact.auth.ClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.ClientAuthInterceptor
import com.tencent.bkrepo.common.artifact.auth.DefaultClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.permission.DefaultPermissionCheckHandler
import com.tencent.bkrepo.common.artifact.permission.PermissionAspect
import com.tencent.bkrepo.common.artifact.permission.PermissionCheckHandler
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMapMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoMethodArgumentResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.multipart.commons.CommonsMultipartResolver
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
    private lateinit var artifactConfiguration: ArtifactConfiguration

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(artifactInfoMethodArgumentResolver())
                resolvers.add(ArtifactFileMethodArgumentResolver())
                resolvers.add(ArtifactFileMapMethodArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                val clientAuthConfig = artifactConfiguration.getClientAuthConfig()
                registry.addInterceptor(clientAuthInterceptor())
                    .addPathPatterns(clientAuthConfig.includePatterns)
                    .excludePathPatterns(clientAuthConfig.excludePatterns)
                super.addInterceptors(registry)
            }
        }
    }

    /**
     * 使用commons-fileupload, 以实现ArtifactFile接口对MultipartFile的适配
     * springboot默认使用的是Servlet3+ StandardMultipartFile
     */
    @Bean(name = ["multipartResolver"])
    fun commonsMultipartResolver(): CommonsMultipartResolver {
        val multipartResolver = CommonsMultipartResolver()
        // 通用制品库文件大小范围不固定，因此不做大小限制
        multipartResolver.setMaxUploadSize(-1)
        multipartResolver.setMaxUploadSizePerFile(-1)
        return multipartResolver
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
