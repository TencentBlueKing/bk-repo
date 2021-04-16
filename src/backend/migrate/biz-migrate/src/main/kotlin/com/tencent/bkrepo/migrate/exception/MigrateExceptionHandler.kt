package com.tencent.bkrepo.migrate.exception

import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class MigrateExceptionHandler {
    @ExceptionHandler(DockerTagInvalidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handlerTagInvalidException(exception: DockerTagInvalidException): Response<Boolean> {
        return ResponseBuilder.build(0, exception.message, false)
    }

    @ExceptionHandler(SyncRequestFormatException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handlerSyncRequestException(exception: SyncRequestFormatException): Response<Void> {
        return ResponseBuilder.fail(CommonMessageCode.PARAMETER_INVALID.getCode(), exception.message)
    }
}
