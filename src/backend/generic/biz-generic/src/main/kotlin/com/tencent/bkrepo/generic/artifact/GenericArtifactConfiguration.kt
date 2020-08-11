package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.security.http.login.BasicAuthLoginHandler
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GenericArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.GENERIC

    @Bean
    fun genericAuthSecurityCustomizer(authenticationManager: AuthenticationManager): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity
                    .disableBasicAuth()
                    .addHttpAuthHandler(BasicAuthLoginHandler(authenticationManager))
            }
        }
    }
}
