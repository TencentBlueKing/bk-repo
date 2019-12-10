package com.tencent.bkrepo.common.artifact.exception

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.JsonUtils
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
    fun handleException(exception: ArtifactResolveException) {
        response(HttpStatus.BAD_REQUEST, exception)
    }

    @ExceptionHandler(ArtifactNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleException(exception: ArtifactNotFoundException) {
        response(HttpStatus.NOT_FOUND, exception)
    }

    @ExceptionHandler(ClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleException(exception: ClientAuthException) {
        HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        response(HttpStatus.UNAUTHORIZED, exception)
    }

    @ExceptionHandler(PermissionCheckException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleException(exception: PermissionCheckException) {
        response(HttpStatus.FORBIDDEN, exception)
    }

    @ExceptionHandler(ArtifactValidateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactValidateException) {
        response(HttpStatus.BAD_REQUEST, exception)
    }

    @ExceptionHandler(ArtifactUploadException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: ArtifactUploadException) {
        response(HttpStatus.INTERNAL_SERVER_ERROR, exception)
    }

    @ExceptionHandler(ArtifactDownloadException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: ArtifactDownloadException) {
        response(HttpStatus.INTERNAL_SERVER_ERROR, exception)
    }

    @ExceptionHandler(UnsupportedMethodException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun handleException(exception: UnsupportedMethodException) {
        response(HttpStatus.METHOD_NOT_ALLOWED, exception)
    }

    @ExceptionHandler(ArtifactException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ArtifactException) {
        response(HttpStatus.BAD_REQUEST, exception)
    }

    private fun logException(exception: ArtifactException) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn("User[$userId] access resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}")
    }

    private fun response(status: HttpStatus, exception: ArtifactException) {
        logException(exception)
        val responseObject = Response.fail(status.value(), exception.message)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        HttpContextHolder.getResponse().writer.println(responseString)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactExceptionHandler::class.java)
    }
}
