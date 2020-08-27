package com.tencent.bkrepo.common.security.exception

import com.tencent.bkrepo.common.api.exception.StatusCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PROMPT
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class SecurityExceptionHandler {

    /**
     * 单独处理认证失败异常，需要添加header
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleException(exception: AuthenticationException): Response<*> {
        HttpContextHolder.getResponse().setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_AUTH_PROMPT)
        return response(exception)
    }

    /**
     * 处理权限相关异常
     */
    @ExceptionHandler(PermissionException::class)
    fun handleException(exception: PermissionException): Response<*> {
        return response(exception)
    }

    fun response(exception: StatusCodeException): Response<*> {
        LoggerHolder.logBusinessException(exception)
        HttpContextHolder.getResponse().status = exception.status.value
        return ResponseBuilder.fail(exception.status.value, exception.message)
    }
}
