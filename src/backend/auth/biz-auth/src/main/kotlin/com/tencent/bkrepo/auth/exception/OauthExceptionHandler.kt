/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.exception

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.service.exception.AbstractExceptionHandler
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ControllerAdvice
class OauthExceptionHandler : AbstractExceptionHandler() {

    @ExceptionHandler(OauthException::class)
    fun handleOauthException(exception: OauthException) {
        LoggerHolder.logException(exception, "[${exception.error}]${exception.errorDescription}", false)
        HttpContextHolder.getResponse().status = exception.status.value
        HttpContextHolder.getResponse().contentType = MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
        HttpContextHolder.getResponse().writer.println(
            OauthError(
                exception.error.value,
                exception.errorDescription
            ).toJsonString()
        )
    }

    data class OauthError(
        @JsonProperty("error")
        val error: String,
        @JsonProperty("error_description")
        val errorDescription: String
    )
}
