package com.tencent.bkrepo.auth.exception.handler


import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 *
 * @author: owenlxu
 * @date: 2020/12/5
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
class DefaultAuthExceptionHandler  {

    //
    // @ExceptionHandler(ClientAuthException::class)
    // fun handleException(exception: ClientAuthException): Response<*> {
    //     HttpContextHolder.getResponse().setHeader(BASIC_AUTH_RESPONSE_HEADER, BASIC_AUTH_RESPONSE_VALUE)
    //     return response(exception)
    // }

}
