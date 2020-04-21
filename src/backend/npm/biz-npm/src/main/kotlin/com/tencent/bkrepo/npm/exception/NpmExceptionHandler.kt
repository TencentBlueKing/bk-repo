package com.tencent.bkrepo.npm.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_HEADER
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_RESPONSE_VALUE
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.npm.pojo.AuthFailInfo
import com.tencent.bkrepo.npm.pojo.NpmAuthFailResponse
import com.tencent.bkrepo.npm.pojo.NpmErrorResponse
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
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
class NpmExceptionHandler {

    @ExceptionHandler(ClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerClientAuthException(exception: ClientAuthException) {
        HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        val responseObject = NpmErrorResponse("Unauthorized", "Authentication required")
        npmResponse(responseObject, exception)
    }

    @ExceptionHandler(NpmClientAuthException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerNpmClientAuthException(exception: NpmClientAuthException) {
        val responseObject = NpmErrorResponse("Unauthorized", "Authentication required")
        npmResponse(responseObject, exception)
    }

    @ExceptionHandler(NpmLoginFailException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerNpmLoginFailException(exception: NpmLoginFailException) {
        val fail = NpmAuthResponse.fail(exception.message)
        npmResponse(fail, exception)
    }

    @ExceptionHandler(NpmTokenIllegalException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlerNpmTokenIllegalException(exception: NpmTokenIllegalException) {
        HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
        val authFailInfo = AuthFailInfo(HttpStatus.UNAUTHORIZED.value(), exception.message)
        val npmAuthFailResponse = NpmAuthFailResponse(errors = listOf(authFailInfo))
        npmResponse(npmAuthFailResponse, exception)
    }

    @ExceptionHandler(NpmArtifactNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerNpmArtifactNotFoundException(exception: NpmArtifactNotFoundException) {
        val responseObject = NpmErrorResponse.notFound()
        npmResponse(responseObject, exception)
    }

    @ExceptionHandler(NpmArtifactExistException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handlerNpmArtifactExistException(exception: NpmArtifactExistException) {
        val responseObject = NpmErrorResponse("forbidden", exception.message)
        npmResponse(responseObject, exception)
    }

    private fun npmResponse(responseObject: NpmErrorResponse, exception: NpmException) {
        logNpmException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun npmResponse(npmAuthResponse: NpmAuthResponse<Void>, exception: NpmException) {
        logNpmException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(npmAuthResponse)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun npmResponse(responseObject: NpmErrorResponse, exception: Exception) {
        logNpmException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun npmResponse(npmAuthFailResponse: NpmAuthFailResponse, exception: NpmException) {
        logNpmException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(npmAuthFailResponse)
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=utf-8"
        response.writer.println(responseString)
    }

    private fun logNpmException(exception: Exception) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn("User[$userId] access resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NpmExceptionHandler::class.java)
    }
}
