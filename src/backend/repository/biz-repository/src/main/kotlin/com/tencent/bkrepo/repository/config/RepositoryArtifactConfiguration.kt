package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Primary
@Configuration
class RepositoryArtifactConfiguration : ArtifactConfiguration {

    @Bean
    fun repositoryAuthSecurityCustomizer(): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity.includePattern("/api/**")
            }
        }
    }
}
