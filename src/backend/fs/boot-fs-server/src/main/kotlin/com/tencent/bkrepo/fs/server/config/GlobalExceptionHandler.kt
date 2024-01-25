/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.config

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.BASIC_AUTH_PROMPT
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.fs.server.exception.RemoteErrorCodeException
import com.tencent.bkrepo.fs.server.utils.LocaleMessageUtils
import com.tencent.bkrepo.fs.server.utils.SpringContextUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.cloud.sleuth.Tracer
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class GlobalExceptionHandler : ErrorWebExceptionHandler {
    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val body = when (ex) {
            is ErrorCodeException -> handlerErrorCodeException(exchange, ex)
            is RemoteErrorCodeException -> handlerRemoteErrorCodeException(exchange, ex)
            else -> handlerError(exchange, ex)
        }
        val bodyBytes = JsonUtils.objectMapper.writeValueAsBytes(body)
        val res = exchange.response.bufferFactory().wrap(bodyBytes)
        return exchange.response.writeWith(Mono.just(res))
    }

    private fun handlerErrorCodeException(exchange: ServerWebExchange, exception: ErrorCodeException): Response<Void> {
        val userId = exchange.attributes[USER_KEY] ?: ANONYMOUS_USER
        val method = exchange.request.method
        val uri = exchange.request.path
        val exceptionName = exception.javaClass.simpleName
        val errorMsg = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
        val fullMessage = "User[$userId] $method [$uri] failed[$exceptionName]: $errorMsg"
        if (exception.status.isServerError()) {
            logger.error(fullMessage)
        } else {
            logger.warn(fullMessage)
        }
        if (exception is AuthenticationException) {
            exchange.response.headers[HttpHeaders.WWW_AUTHENTICATE] = BASIC_AUTH_PROMPT
        }
        exchange.response.rawStatusCode = exception.status.value
        return Response(exception.messageCode.getCode(), errorMsg, null, getTraceId())
    }

    private fun handlerRemoteErrorCodeException(
        exchange: ServerWebExchange,
        exception: RemoteErrorCodeException
    ): Response<Void> {
        logger.warn("[${exception.methodKey}][${exception.errorCode}]${exception.errorMessage}")
        exchange.response.rawStatusCode = HttpStatus.BAD_REQUEST.value
        return Response(exception.errorCode, exception.errorMessage, null, getTraceId())
    }

    private fun handlerError(exchange: ServerWebExchange, ex: Throwable): Response<Void> {
        val errorMsg = CommonMessageCode.SYSTEM_ERROR.getKey()
        exchange.response.rawStatusCode = HttpStatus.INTERNAL_SERVER_ERROR.value
        logger.error(errorMsg, ex)
        return Response(CommonMessageCode.SYSTEM_ERROR.getCode(), errorMsg, null, getTraceId())
    }

    private fun getTraceId(): String? {
        return try {
            SpringContextUtils.getBean<Tracer>().currentSpan()?.context()?.traceId()
        } catch (_: BeansException) {
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
