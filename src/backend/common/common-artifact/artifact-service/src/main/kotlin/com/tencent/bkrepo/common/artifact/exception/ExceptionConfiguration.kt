package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

@Configuration
@Import(
    DefaultArtifactExceptionHandler::class,
    ArtifactExceptionResponseAdvice::class
)
class ExceptionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun exceptionTranslator() = object :
        ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse) = payload
    }
}
