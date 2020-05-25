package com.tencent.bkrepo.pypi.exception

import com.tencent.bkrepo.pypi.pojo.PypiExceptionResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class PypiExceptionHandler {
    @ExceptionHandler(PypiUnSupportCompressException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleException(exception: PypiUnSupportCompressException): PypiExceptionResponse {
        return PypiExceptionResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.toString(), exception.message)
    }
}
