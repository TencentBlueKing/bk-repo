package com.tencent.bkrepo.helm.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.pojo.HelmErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class HelmExceptionHandler {

    @ExceptionHandler(AuthenticationException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerClientAuthException(exception: AuthenticationException) {
        val responseObject = HelmErrorResponse(HttpStatus.UNAUTHORIZED.reasonPhrase)
        helmResponse(responseObject, exception)
    }

    @ExceptionHandler(HelmIndexFreshFailException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handlerHelmIndexFreshFailException(exception: HelmIndexFreshFailException) {
        val responseObject = HelmErrorResponse(exception.message)
        helmResponse(responseObject, exception)
    }

    @ExceptionHandler(HelmFileAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handlerHelmFileAlreadyExistsException(exception: HelmFileAlreadyExistsException) {
        val responseObject = HelmErrorResponse(exception.message)
        helmResponse(responseObject, exception)
    }

    @ExceptionHandler(HelmErrorInvalidProvenanceFileException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handlerHelmErrorInvalidProvenanceFileException(exception: HelmErrorInvalidProvenanceFileException) {
        val responseObject = HelmErrorResponse(exception.message)
        helmResponse(responseObject, exception)
    }

    @ExceptionHandler(HelmFileNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerHelmFileNotFoundException(exception: HelmFileNotFoundException) {
        val responseObject = HelmErrorResponse(exception.message)
        helmResponse(responseObject, exception)
    }

    private fun helmResponse(responseObject: HelmErrorResponse, exception: Exception) {
        logHelmException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun logHelmException(exception: Exception) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn(
            "User[$userId] access resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HelmExceptionHandler::class.java)
    }
}
