package com.tencent.repository.common.service.exception

import com.tencent.repository.common.api.exception.ErrorCodeException
import com.tencent.repository.common.api.pojo.Result
import com.tencent.repository.common.service.utils.MessageCodeUtils
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

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleGlobalException(exception: ErrorCodeException): Result<Void> {
        logger.error("Failed with exception exception:$exception")

        val errorMsg = MessageCodeUtils.generateResponseDataObject<String>(exception.errorCode)
        return Result(
                status = HttpStatus.BAD_REQUEST.value(),
                message = errorMsg.message ?: exception.message ?: "Unknown Error: ${exception.errorCode}"
        )

    }

    companion object {
        val logger = LoggerFactory.getLogger(this.javaClass)!!
    }


}