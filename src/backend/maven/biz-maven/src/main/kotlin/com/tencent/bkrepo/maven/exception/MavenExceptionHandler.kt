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

package com.tencent.bkrepo.maven.exception

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.maven.pojo.MavenExceptionResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    @ExceptionHandler(MavenBadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: MavenBadRequestException) {
        val response = HttpContextHolder.getResponse()
        response.status = HttpStatus.BAD_REQUEST.value()
        response.contentType = MediaTypes.APPLICATION_JSON
        response.writer.print(String.format(gavcBadRequestPlainFormat, exception.message))
        response.writer.flush()
    }

    @ExceptionHandler(MavenMetadataChecksumException::class)
    @ResponseStatus(HttpStatus.OK)
    fun handleException(exception: MavenMetadataChecksumException) {
        val response = HttpContextHolder.getResponse()
        response.status = HttpStatus.OK.value()
        response.writer.flush()
    }

    @ExceptionHandler(MavenArtifactFormatException::class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    fun handleException(exception: MavenArtifactFormatException): MavenExceptionResponse {
        logger.error("$exception")
        return MavenExceptionResponse(HttpStatus.NOT_ACCEPTABLE.toString(), exception.message)
    }

    companion object {
        const val gavcBadRequestPlainFormat = "\"errors\" : [ {\n" +
            "    \"status\" : 400,\n" +
            "    \"message\" : \"%s\"\n" +
            "  } ]"
        val logger: Logger = LoggerFactory.getLogger(MavenExceptionHandler::class.java)
    }
}
