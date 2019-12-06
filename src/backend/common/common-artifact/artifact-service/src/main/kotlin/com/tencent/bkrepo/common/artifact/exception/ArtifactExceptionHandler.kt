package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ANONYMOUS_USER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_VALUE
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

/**
 *
 * @author: carrypan
 * @date: 2019/12/5
 */
@ControllerAdvice
class ArtifactExceptionHandler {

    @ExceptionHandler(ArtifactResolveException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactResolveException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.BAD_REQUEST.value(), exception.message)
    }

    @ExceptionHandler(ArtifactNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleException(exception: ArtifactNotFoundException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.NOT_FOUND.value(), exception.message)
    }

    @ExceptionHandler(ClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleException(exception: ClientAuthException): Response<Void> {
        logException(exception)
        val response = HttpContextHolder.getResponse()
        response.setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        return Response.fail(HttpStatus.UNAUTHORIZED.value(), exception.message)
    }

    @ExceptionHandler(PermissionCheckException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleException(exception: PermissionCheckException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.FORBIDDEN.value(), exception.message)
    }

    @ExceptionHandler(ArtifactValidateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactValidateException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.BAD_REQUEST.value(), exception.message)
    }

    @ExceptionHandler(ArtifactUploadException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: ArtifactUploadException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.message)
    }

    @ExceptionHandler(ArtifactDownloadException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: ArtifactDownloadException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.message)
    }

    @ExceptionHandler(UnsupportedMethodException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun handleException(exception: UnsupportedMethodException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.METHOD_NOT_ALLOWED.value(), exception.message)
    }

    @ExceptionHandler(ArtifactException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactException): Response<Void> {
        logException(exception)
        return Response.fail(HttpStatus.BAD_REQUEST.value(), exception.message)
    }

    private fun logException(exception: ArtifactException) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn("User[$userId] access resource[$uri] failed[${exception.javaClass.name}]: ${exception.message}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactExceptionHandler::class.java)
    }
}
