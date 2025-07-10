/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.service.otel.web

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.undertow.servlet.spec.HttpServletRequestImpl
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

class OtelWebFilter : Filter {

    /**
     * Undertow对于url没有实现RFC 2396, []不编码也能正常处理。
     *
     * 但是sleuth实现日志追踪[org.springframework.cloud.sleuth.otel.bridge.SpringHttpServerAttributesExtractor.target]时，
     * 使用了[java.net.URI.create],在有未编码的特殊字符时会导致500
     */
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequestImpl) {
            val url = request.requestURL.toString()
            try {
                URI.create(url)
            } catch (ignore: IllegalArgumentException) {
                logger.warn("illegal url: $url, request method: ${request.method}")
                try {
                    request.exchange.requestURI = URLEncoder.encode(
                        URLDecoder.decode(request.exchange.requestURI.replace("+",  "%2b"), Charsets.UTF_8.name()),
                        Charsets.UTF_8.name()
                    ).replace("+", "%20").replace("%2f", "/")
                } catch (e: Exception) {
                    require(response is HttpServletResponse)
                    response.status = HttpStatus.BAD_REQUEST.value
                    response.contentType = MediaTypes.APPLICATION_JSON
                    response.writer.println(
                        ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, e.message).toJsonString()
                    )
                    return
                }
            }
        }
        chain.doFilter(request, response)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OtelWebFilter::class.java)
    }
}
