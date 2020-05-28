package com.tencent.bkrepo.composer.exception

import com.tencent.bkrepo.composer.pojo.ComposerExceptionResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ComposerExceptionHandler {
    @ExceptionHandler(ComposerUnSupportCompressException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleException(exception: ComposerUnSupportCompressException): ComposerExceptionResponse {
        return ComposerExceptionResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.toString(), exception.message)
    }
}
