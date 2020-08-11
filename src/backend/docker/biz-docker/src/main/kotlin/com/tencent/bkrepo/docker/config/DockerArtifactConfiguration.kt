package com.tencent.bkrepo.docker.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.docker.auth.DockerBasicAuthLoginHandler
import com.tencent.bkrepo.docker.auth.DockerJwtAuthHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * config the path to validate privilege
 * @author: owenlxu
 * @date: 2019/11/27
 */

@Configuration
class DockerArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.DOCKER

    @Bean
    fun dockerAuthSecurityCustomizer(
        jwtProperties: JwtAuthProperties,
        authenticationManager: AuthenticationManager
    ): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity.disableBasicAuth()
                    .addHttpAuthHandler(DockerBasicAuthLoginHandler(jwtProperties, authenticationManager))
                    .addHttpAuthHandler(DockerJwtAuthHandler(jwtProperties))
                    .addExcludePattern("/v2/auth")
                    .addExcludePattern("/v2/_catalog")
                    .addExcludePattern("/v2/*/*/*/tags/list")
            }
        }
    }
}
