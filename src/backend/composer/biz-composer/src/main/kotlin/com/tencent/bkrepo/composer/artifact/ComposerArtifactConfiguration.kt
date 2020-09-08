package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.composer.pojo.ComposerExceptionResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

@Configuration
class ComposerArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.COMPOSER

    @Bean
    fun exceptionResponseTranslator() = object :
        ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return ComposerExceptionResponse(StringPool.EMPTY, payload.message.orEmpty())
        }
    }
}
