package com.tencent.bkrepo.common.artifact.exception.handler

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_VALUE
import com.tencent.bkrepo.common.artifact.exception.ArtifactException
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResolveException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 *
 * @author: carrypan
 * @date: 2019/12/5
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class ArtifactExceptionHandler: AbstractExceptionHandler() {

    @ExceptionHandler(ArtifactResolveException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactResolveException): Response<*> {
        return response(HttpStatus.BAD_REQUEST, exception)
    }

    @ExceptionHandler(ArtifactNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleException(exception: ArtifactNotFoundException): Response<*> {
        return response(HttpStatus.NOT_FOUND, exception)
    }

    @ExceptionHandler(ClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleException(exception: ClientAuthException): Response<*> {
        HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        return response(HttpStatus.UNAUTHORIZED, exception)
    }

    @ExceptionHandler(PermissionCheckException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleException(exception: PermissionCheckException): Response<*> {
        return response(HttpStatus.FORBIDDEN, exception)
    }

    @ExceptionHandler(ArtifactValidateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactValidateException): Response<*> {
        return response(HttpStatus.BAD_REQUEST, exception)
    }

    @ExceptionHandler(UnsupportedMethodException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun handleException(exception: UnsupportedMethodException): Response<*> {
        return response(HttpStatus.METHOD_NOT_ALLOWED, exception)
    }

    @ExceptionHandler(ArtifactException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactException): Response<*> {
        return response(HttpStatus.BAD_REQUEST, exception)
    }
}
