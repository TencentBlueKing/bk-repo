package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.exception.response.ArtifactExceptionResponseAdvice
import com.tencent.bkrepo.common.artifact.exception.response.ExceptionResponseTranslator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

@Configuration
@Import(ArtifactExceptionHandler::class,
    ArtifactExceptionResponseAdvice::class)
class ExceptionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun exceptionTranslator() = object : ExceptionResponseTranslator {
        override fun translate(
            response: Response<*>,
            request: ServerHttpRequest,
            response1: ServerHttpResponse
        ) = response
    }
}