/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.conan.exception

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.otel.util.TraceHeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.conan.pojo.response.ConanErrorResponse
import com.tencent.bkrepo.conan.pojo.response.ConanResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice("com.tencent.bkrepo.conan")
class ConanExceptionHandler {

    @ExceptionHandler(ConanFileNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerConanFileNotFoundException(exception: ConanFileNotFoundException) {
        val responseObject = ConanResponse.errorResponse(
            ConanErrorResponse(exception.message, HttpStatus.NOT_FOUND.value())
        )
        conanResponse(responseObject, exception)
    }

    @ExceptionHandler(ConanRecipeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerConanRecipeNotFoundException(exception: ConanRecipeNotFoundException) {
        val responseObject = ConanResponse.errorResponse(
            ConanErrorResponse(exception.message, HttpStatus.NOT_FOUND.value())
        )
        conanResponse(responseObject, exception)
    }

    @ExceptionHandler(ConanSearchNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlerConanRecipeNotFoundException(exception: ConanSearchNotFoundException) {
        conanResponse(exception.message!!, exception)
    }


    private fun conanResponse(exception: ErrorCodeException) {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
        val responseObject = ConanResponse.errorResponse(
            ConanErrorResponse(errorMessage, exception.status.value)
        )
        conanResponse(responseObject, exception)
    }

    private fun conanResponse(
        responseObject: Any,
        exception: Exception
    ) {
        logConanException(exception)
        val responseString = JsonUtils.objectMapper.writeValueAsString(responseObject)
        val httpResponse = HttpContextHolder.getResponse()
        TraceHeaderUtils.setResponseHeader()
        httpResponse.contentType = MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
        httpResponse.writer.println(responseString)
    }

    private fun logConanException(exception: Exception) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        logger.warn(
            "User[$userId] access conan resource[$uri] failed[${exception.javaClass.simpleName}]: ${exception.message}"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConanExceptionHandler::class.java)
    }
}
