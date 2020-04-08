package com.tencent.bkrepo.helm.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.JsonUtils
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
class HelmExceptionHandler {
    @ExceptionHandler(HelmIndexFreshFailException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handlerHelmIndexFreshFailException(exception: HelmIndexFreshFailException) {
        response(HttpStatus.INTERNAL_SERVER_ERROR, exception)
    }

    @ExceptionHandler(HelmFileAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerHelmFileAlreadyExistsException(exception: HelmFileAlreadyExistsException) {
        val responseObject = mapOf("error" to exception.message)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun response(status: HttpStatus, exception: HelmException) {
        logHelmException(exception)
        val responseObject = ResponseBuilder.fail(status.value(), exception.message)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun logHelmException(exception: HelmException) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn("User[$userId] access resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HelmExceptionHandler::class.java)
    }
}
