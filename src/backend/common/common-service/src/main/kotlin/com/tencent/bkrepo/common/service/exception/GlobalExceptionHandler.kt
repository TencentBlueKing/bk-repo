package com.tencent.bkrepo.common.service.exception

import com.netflix.client.ClientException
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.MessageCodeUtils
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
        logger.warn("ExternalErrorCodeException[${exception.methodKey}]: [${exception.errorCode}]${exception.errorMessage}")

        return Response.fail(exception.errorCode, exception.errorMessage ?: "")
    }

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ErrorCodeException): Response<Void> {
        val errorMsg = MessageCodeUtils.generateResponseDataObject<String>(exception.errorCode, exception.defaultMessage, exception.params)

        logger.warn("ErrorCodeException: [${exception.errorCode}]$errorMsg")

        return Response.fail(exception.errorCode, errorMsg.message ?: exception.message ?: "Unknown Error: ${exception.errorCode}")
    }

    @ExceptionHandler(HystrixRuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: HystrixRuntimeException): Response<Void> {
        var causeMessage = exception.cause?.message
        var responseMessage = "内部依赖服务调用异常"
        if (exception.failureType == FailureType.COMMAND_EXCEPTION) {
            if(exception.cause?.cause is ClientException) {
                causeMessage = (exception.cause?.cause as ClientException).errorMessage
            }
        }
        else if (exception.failureType == FailureType.SHORTCIRCUIT) {
            responseMessage = "内部依赖服务被熔断"
        }
        logger.error("HystrixRuntimeException[${exception.failureType}]: ${exception.message} Cause: $causeMessage")
        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), responseMessage)
    }


    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): Response<Void> {
        logger.error("Exception: ${exception.message}", exception)
        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "访问后台数据失败，已通知产品、开发，请稍后重试")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
