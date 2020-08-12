package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_ADD_USER_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_USER_LOGOUT_MAPPING_URI
import com.tencent.bkrepo.npm.pojo.NpmErrorResponse
import org.springframework.context.annotation.Bean
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component

@Component
class NpmArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.NPM

    @Bean
    fun npmAuthSecurityCustomizer(
        authenticationManager: AuthenticationManager,
        jwtProperties: JwtAuthProperties
    ): HttpAuthSecurityCustomizer {
        return object : HttpAuthSecurityCustomizer {
            override fun customize(httpAuthSecurity: HttpAuthSecurity) {
                httpAuthSecurity.disableBasicAuth()
                    .addHttpAuthHandler(NpmLoginAuthHandler(authenticationManager, jwtProperties))
            }
        }
    }

    @Bean
    fun exceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return NpmErrorResponse(payload.message.orEmpty(), StringPool.EMPTY)
        }
    }
}
