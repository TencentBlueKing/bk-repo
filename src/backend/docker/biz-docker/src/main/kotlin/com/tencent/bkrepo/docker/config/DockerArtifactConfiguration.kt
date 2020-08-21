package com.tencent.bkrepo.docker.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.docker.auth.DockerBasicAuthLoginHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * config the path to validate privilege
 */
@Configuration
class DockerArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.DOCKER

    @Bean
    fun dockerAuthSecurityCustomizer(
        authenticationManager: AuthenticationManager,
        jwtProperties: JwtAuthProperties
    ): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity.disableBasicAuth()
                    .addHttpAuthHandler(DockerBasicAuthLoginHandler(authenticationManager, jwtProperties))
                    .excludePattern("/v2/_catalog")
                    .excludePattern("/v2/*/*/*/tags/list")
            }
        }
    }
}
