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

package com.tencent.bkrepo.fs.server.context

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.StringTokenizer

object ReactiveRequestContextHolder {

    val REQUEST_CONTEXT_KEY = RequestContext::class.java

    suspend fun getRequest(): ServerHttpRequest {
        return Mono.deferContextual {
            Mono.just(it.get(REQUEST_CONTEXT_KEY))
        }.awaitSingle().request
    }

    suspend fun getResponse(): ServerHttpResponse {
        return Mono.deferContextual {
            Mono.just(it.get(REQUEST_CONTEXT_KEY))
        }.awaitSingle().response
    }

    suspend fun getWebExchange(): ServerWebExchange {
        return Mono.deferContextual {
            Mono.just(it.get(REQUEST_CONTEXT_KEY))
        }.awaitSingle().exchange
    }

    suspend fun getClientAddress(): String {
        val request = getRequest()
        val headers = request.headers
        var address = headers[HttpHeaders.X_FORWARDED_FOR]?.first()
        address = if (address.isNullOrBlank()) {
            headers[HttpHeaders.X_REAL_IP]?.first()
        } else {
            StringTokenizer(address, StringPool.COMMA).nextToken()
        }
        if (address.isNullOrBlank()) {
            address = headers[HttpHeaders.PROXY_CLIENT_IP]?.first()
        }
        if (address.isNullOrBlank()) {
            address = request.remoteAddress?.address?.hostAddress
        }
        if (address.isNullOrBlank()) {
            address = StringPool.UNKNOWN
        }
        // 对于通过多个代理的情况，第一个IP为客户端真实IP，多个IP按照','分割
        return address
    }

    fun getWebExchangeMono(): Mono<ServerWebExchange> {
        return Mono.deferContextual {
            Mono.just(it.get(REQUEST_CONTEXT_KEY).exchange)
        }
    }
}
