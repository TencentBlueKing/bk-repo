/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.s3.exception

import com.tencent.bkrepo.s3.artifact.response.S3ExceptionCommonResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * S3统一异常处理
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice("com.tencent.bkrepo.s3")
class S3ExceptionHandler {
    /**
     * 签名认证失败
     */
    @ExceptionHandler(AWS4AuthenticationException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleException(exception: AWS4AuthenticationException) {
        S3ExceptionCommonResponse.buildErrorResponse(exception)
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(S3NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleException(exception: S3NotFoundException) {
        S3ExceptionCommonResponse.buildErrorResponse(exception)
    }

    /**
     * 没权限访问
     */
    @ExceptionHandler(S3AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleException(exception: S3AccessDeniedException) {
        S3ExceptionCommonResponse.buildErrorResponse(exception)
    }

    /**
     * 系统内部错误
     */
    @ExceptionHandler(S3InternalException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: S3InternalException) {
        S3ExceptionCommonResponse.buildErrorResponse(exception)
    }
}
