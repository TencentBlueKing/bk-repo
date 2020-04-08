package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.exception.response.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
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
    override fun getClientAuthConfig(): ClientAuthConfig = ClientAuthConfig(
        includePatterns = listOf("/**"),
        excludePatterns = listOf(NPM_ADD_USER_MAPPING_URI, NPM_USER_LOGOUT_MAPPING_URI)
    )

    @Bean
    fun exceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return NpmErrorResponse(payload.message.orEmpty(), StringPool.EMPTY)
        }
    }
}
