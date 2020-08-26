package com.tencent.bkrepo.rpm.exception

import com.tencent.bkrepo.rpm.pojo.RpmExceptionResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class RpmExceptionHandler {

    @ExceptionHandler(RpmRequestParamMissException::class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    fun handleException(exception: RpmRequestParamMissException): RpmExceptionResponse {
        return RpmExceptionResponse(HttpStatus.PRECONDITION_FAILED.toString(), exception.message)
    }

    @ExceptionHandler(RpmIndexTypeResolveException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: RpmIndexTypeResolveException): RpmExceptionResponse {
        return RpmExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), exception.message)
    }

    @ExceptionHandler(RpmIndexNotFoundException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: RpmIndexNotFoundException): RpmExceptionResponse {
        return RpmExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), exception.message)
    }

    @ExceptionHandler(RpmArtifactMetadataResolveException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: RpmArtifactMetadataResolveException): RpmExceptionResponse {
        return RpmExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), exception.message)
    }
}
