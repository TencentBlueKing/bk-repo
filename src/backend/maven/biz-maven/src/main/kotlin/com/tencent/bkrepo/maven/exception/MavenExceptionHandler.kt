package com.tencent.bkrepo.maven.exception

import com.tencent.bkrepo.maven.pojo.MavenExceptionResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class MavenExceptionHandler {
    @ExceptionHandler(MavenPathParserException::class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    fun handleException(exception: MavenPathParserException): MavenExceptionResponse {
        return MavenExceptionResponse(HttpStatus.PRECONDITION_FAILED.toString(), exception.message)
    }
}
