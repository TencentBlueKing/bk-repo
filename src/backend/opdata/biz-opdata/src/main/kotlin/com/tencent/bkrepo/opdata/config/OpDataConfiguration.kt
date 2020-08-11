package com.tencent.bkrepo.opdata.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 *
 * @author: owenlxu
 * @date: 2020/01/03
 */
@Configuration
class OpDataConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.OPDATA

    @Bean
    fun opDataAuthSecurityCustomizer(): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity.disableJwtAuth()
            }
        }
    }

}
