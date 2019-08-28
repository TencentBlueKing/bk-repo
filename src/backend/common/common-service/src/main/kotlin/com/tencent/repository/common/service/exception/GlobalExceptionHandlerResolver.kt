package com.tencent.repository.common.service.exception

import com.tencent.repository.common.api.exception.ErrorCodeException
import com.tencent.repository.common.api.pojo.Result
import com.tencent.repository.common.service.util.MessageCodeUtils
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleGlobalException(exception: ErrorCodeException): Result<Void> {
        logger.error("Failed with exception exception:$exception")

        val errorMsg = MessageCodeUtils.generateResponseDataObject<String>(exception.errorCode)
        return Result(
                code = exception.errorCode,
                message = errorMsg.message ?: exception.message ?: "Unknown Error: ${exception.errorCode}"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandlerResolver::class.java)
    }
}
