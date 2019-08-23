package com.tencent.repository.common.web.exception

import com.tencent.devops.common.api.exception.CustomException
import com.tencent.repository.common.api.pojo.Result
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * 统一异常处理
 */
@RestControllerAdvice
class GlobalExceptionHandlerResolver {

    @ExceptionHandler(CustomException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGlobalException(exception: CustomException): Result<Void> {
        logger.error("Failed with exception exception:$exception")
        return Result(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message = exception.message ?: "Internal Exception"
        )

    }

    companion object {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandlerResolver.javaClass)!!
    }


}