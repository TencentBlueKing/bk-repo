package com.tencent.bkrepo.common.service.exception

import com.netflix.client.ClientException
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ExternalErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ExternalErrorCodeException): Response<Void> {
        logger.warn("${exception.javaClass.simpleName}: [${exception.methodKey}][${exception.errorCode}]${exception.errorMessage}")

        return Response.fail(exception.errorCode, exception.errorMessage ?: "")
    }

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ErrorCodeException): Response<Void> {
        val errorMsg = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
        logger.warn("${exception.javaClass.simpleName}: [${exception.messageCode.getCode()}]$errorMsg")

        return Response.fail(exception.messageCode.getCode(), errorMsg)
    }

    @ExceptionHandler(HystrixRuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: HystrixRuntimeException): Response<Void> {
        var causeMessage = exception.cause?.message
        var messageCode = CommonMessageCode.SERVICE_CALL_ERROR
        if (exception.failureType == FailureType.COMMAND_EXCEPTION) {
            if (exception.cause?.cause is ClientException) {
                causeMessage = (exception.cause?.cause as ClientException).errorMessage
            }
        } else if (exception.failureType == FailureType.SHORTCIRCUIT) {
            messageCode = CommonMessageCode.SERVICE_CIRCUIT_BREAKER
        }
        logger.error("${exception.javaClass.simpleName}: [${exception.failureType}]${exception.message} Cause: $causeMessage")
        return response(messageCode)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): Response<Void> {
        logger.error("${exception.javaClass.simpleName}: ${exception.message}", exception)
        return response(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun response(messageCode: MessageCode): Response<Void> {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, null)
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
