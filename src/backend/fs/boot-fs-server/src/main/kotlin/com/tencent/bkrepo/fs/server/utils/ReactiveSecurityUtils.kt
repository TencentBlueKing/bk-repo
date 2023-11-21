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

package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono

object ReactiveSecurityUtils {

    fun ServerRequest.bearerToken(): String? {
        val authHeader = headers().header(HttpHeaders.AUTHORIZATION).firstOrNull()
        return if (authHeader?.startsWith(BEARER_AUTH_PREFIX) == true) {
            authHeader.removePrefix(BEARER_AUTH_PREFIX)
        } else {
            authHeader
        }
    }

    suspend fun getUser(): String {
        return ReactiveRequestContextHolder
            .getWebExchange()
            .attributes[USER_KEY] as? String ?: ANONYMOUS_USER
    }

    fun getUserMono(): Mono<String> {
        return ReactiveRequestContextHolder
            .getWebExchangeMono().map {
                it.attributes[USER_KEY] as? String ?: ANONYMOUS_USER
            }
    }
}
