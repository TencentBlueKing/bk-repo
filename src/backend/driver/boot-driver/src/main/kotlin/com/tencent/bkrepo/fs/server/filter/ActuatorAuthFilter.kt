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

package com.tencent.bkrepo.fs.server.filter

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.fs.server.filterAndAwait
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain

/**
 * 处理Actuator Endpoint的认证。
 * */
class ActuatorAuthFilter(private val rAuthClient: RAuthClient) : CoWebFilter {

    private val antPathMatcher = AntPathMatcher()

    override suspend fun doFilter(exchange: ServerWebExchange, chain: WebFilterChain) {
        val request = exchange.request
        val uri = request.path.value()
        // 非Actuator端点
        if (!antPathMatcher.match(MANAGEMENT_ENDPOINT, uri)) {
            return chain.filterAndAwait(exchange)
        }
        // info/health端点不需要鉴权
        if (antPathMatcher.match(HEALTH_ENDPOINT, uri) || antPathMatcher.match(INFO_ENDPOINT, uri)) {
            return chain.filterAndAwait(exchange)
        }
        val authorizationHeader = request.headers[HttpHeaders.AUTHORIZATION]?.firstOrNull()
            ?: throw AuthenticationException("Empty authorization value.")
        val (username, password) = BasicAuthUtils.decode(authorizationHeader)
        val tokenRes = rAuthClient.checkToken(username, password).awaitSingle()
        if (tokenRes.data != true) {
            throw AuthenticationException()
        }
        val userDetailRes = rAuthClient.detail(username).awaitSingle()
        if (userDetailRes.data?.admin != true) {
            throw PermissionException()
        }
        return chain.filterAndAwait(exchange)
    }

    private companion object {
        private const val HEALTH_ENDPOINT = "/actuator/health/**"
        private const val INFO_ENDPOINT = "/actuator/info/**"
        private const val MANAGEMENT_ENDPOINT = "/actuator/**"
    }
}
