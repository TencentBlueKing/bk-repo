package com.tencent.bkrepo.common.service.exception

import com.netflix.client.ClientException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.MessageCodeUtils
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@RestControllerAdvice
class GlobalExceptionHandlerResolver {

    @ExceptionHandler(ErrorCodeException::class)
    fun handleErrorCodeException(exception: ErrorCodeException): Response<Void> {
        val errorMsg = MessageCodeUtils.generateResponseDataObject<String>(exception.errorCode)

        logger.error("Failed with error code exception:[${exception.errorCode}-$errorMsg]")

        return Response.fail(exception.errorCode, errorMsg.message ?: exception.message ?: "Unknown Error: ${exception.errorCode}")
    }

    @ExceptionHandler(ClientException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleClientException(exception: ClientException): Response<Void> {
        logger.error("Failed with client exception:[$exception]")

        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "内部依赖服务异常")
    }

    @ExceptionHandler(FeignException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleFeignException(exception: FeignException): Response<Void> {
        logger.error("Failed with feign exception:[$exception]")

        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "内部依赖服务调用异常")
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): Response<Void> {
        logger.error("Failed with other exception:[$exception]")

        return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "访问后台数据失败，已通知产品、开发，请稍后重试")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandlerResolver::class.java)
    }
}
