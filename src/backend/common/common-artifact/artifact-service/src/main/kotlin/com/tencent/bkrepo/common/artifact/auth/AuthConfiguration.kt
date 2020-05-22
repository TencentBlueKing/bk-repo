package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.artifact.auth.basic.BasicClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.core.AuthService
import com.tencent.bkrepo.common.artifact.auth.core.ClientAuthInterceptor
import com.tencent.bkrepo.common.artifact.auth.jwt.JwtClientAuthHandler
import com.tencent.bkrepo.common.artifact.auth.jwt.JwtProvider
import com.tencent.bkrepo.common.artifact.auth.platform.PlatformClientAuthHandler
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.AuthProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(AuthProperties::class)
class AuthConfiguration {

    @Autowired
    private lateinit var artifactConfiguration: ArtifactConfiguration

    @Bean
    fun artifactAuthConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                val clientAuthConfig = artifactConfiguration.getClientAuthConfig()
                registry.addInterceptor(clientAuthInterceptor())
                    .addPathPatterns(clientAuthConfig.includePatterns)
                    .excludePathPatterns(clientAuthConfig.excludePatterns)
                super.addInterceptors(registry)
            }
        }
    }

    @Bean
    fun authService() = AuthService()

    @Bean
    fun clientAuthInterceptor() = ClientAuthInterceptor()

    @Bean
    fun jwtProvider() = JwtProvider()

    @Bean("basic")
    fun basicClientAuthHandler() = BasicClientAuthHandler()

    @Bean("platform")
    fun platformClientAuthHandler() = PlatformClientAuthHandler()

    @Bean("jet")
    fun jwtClientAuthHandler() = JwtClientAuthHandler()
}
