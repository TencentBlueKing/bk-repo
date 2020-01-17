package com.tencent.bkrepo.npm.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactExceptionHandler
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@RestControllerAdvice
class NpmExceptionHandler {

    @ExceptionHandler(NpmClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerNpmClientAuthException(exception: NpmClientAuthException) {
        response(HttpStatus.UNAUTHORIZED, exception)
    }

    @ExceptionHandler(NpmTokenIllegalException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerNpmTokenIllegalException(exception: NpmTokenIllegalException) {
        response(HttpStatus.UNAUTHORIZED, exception)
    }

    private fun response(status: HttpStatus, exception: NpmException) {
        logNpmException(exception)
        val responseObject = ResponseBuilder.fail(status.value(), exception.message)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun logNpmException(exception: NpmException) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn("User[$userId] access resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactExceptionHandler::class.java)
    }
}
