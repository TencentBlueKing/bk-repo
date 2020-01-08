package com.tencent.bkrepo.common.service.exception

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.netflix.client.ClientException
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.log.LoggerHolder.logException
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
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
        logException(exception, "[${exception.methodKey}][${exception.errorCode}]${exception.errorMessage}")
        return Response.fail(exception.errorCode, exception.errorMessage ?: "")
    }

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ErrorCodeException): Response<Void> {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
        logException(exception, "[${exception.messageCode.getCode()}]$errorMessage")
        return Response.fail(exception.messageCode.getCode(), errorMessage)
    }

    /**
     * 参数处理异常
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: MissingServletRequestParameterException): Response<Void> {
        val messageCode = CommonMessageCode.PARAMETER_MISSING
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, arrayOf(exception.parameterName))
        logException(exception, "[${messageCode.getCode()}]$errorMessage")
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    /**
     * 参数处理异常
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: HttpMessageNotReadableException): Response<Void> {
        val messageCode = CommonMessageCode.REQUEST_CONTENT_INVALID
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, null)
        logException(exception, "[${messageCode.getCode()}]$errorMessage")
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    /**
     * 参数处理异常
     */
    @ExceptionHandler(MissingKotlinParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: MissingKotlinParameterException): Response<Void> {
        val messageCode = CommonMessageCode.PARAMETER_MISSING
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, arrayOf(exception.parameter.name ?: ""))
        logException(exception, "[${messageCode.getCode()}]$errorMessage")
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    fun handleException(exception: HttpRequestMethodNotSupportedException): Response<Void> {
        val messageCode = CommonMessageCode.OPERATION_UNSUPPORTED
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, null)
        logException(exception, "[${messageCode.getCode()}]$errorMessage")
        return Response.fail(messageCode.getCode(), errorMessage)
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
        logException(exception, "[${exception.failureType}]${exception.message} Cause: $causeMessage", LoggerHolder.SYSTEM)
        return response(messageCode)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): Response<Void> {
        logException(exception, exception.message, LoggerHolder.SYSTEM, true)
        return response(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun response(messageCode: MessageCode): Response<Void> {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, null)
        return Response.fail(messageCode.getCode(), errorMessage)
    }
}
