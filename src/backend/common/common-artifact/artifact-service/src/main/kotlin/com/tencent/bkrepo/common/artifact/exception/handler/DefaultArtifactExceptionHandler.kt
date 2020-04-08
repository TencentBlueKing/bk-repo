package com.tencent.bkrepo.common.artifact.exception.handler

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_VALUE
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 *
 * @author: carrypan
 * @date: 2019/12/5
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class DefaultArtifactExceptionHandler : AbstractArtifactExceptionHandler() {

    @ExceptionHandler(ClientAuthException::class)
    fun handleException(exception: ClientAuthException): Response<*> {
        HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        return response(exception)
    }

    @ExceptionHandler(ArtifactException::class)
    fun handleException(exception: ArtifactException): Response<*> {
        return response(exception)
    }
}
