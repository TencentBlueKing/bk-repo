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

package com.tencent.bkrepo.conan.service.impl

import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.conan.service.ConanAuthService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.springframework.stereotype.Service

@Service
class ConanAuthServiceImpl(
    private val jwtProperties: JwtAuthProperties
) : ConanAuthService {

    private val signingKey = JwtUtils.createSigningKey(jwtProperties.secretKey)

    override fun checkCredentials(token: String): String {
        val jwtToken = token.removePrefix(BEARER_AUTH_PREFIX).trim()
        return try {
            JwtUtils.validateToken(signingKey, jwtToken).body.subject
        } catch (exception: ExpiredJwtException) {
            throw AuthenticationException("Expired token")
        } catch (exception: JwtException) {
            throw AuthenticationException("Invalid token")
        } catch (exception: IllegalArgumentException) {
            throw AuthenticationException("Empty token")
        }
    }

    override fun authenticate(content: String): String {
        return try {
            val pair = BasicAuthUtils.decode(content)
            JwtUtils.generateToken(signingKey, jwtProperties.expiration, pair.first)
        } catch (ignored: IllegalArgumentException) {
            throw AuthenticationException("Invalid authorization value.")
        }
    }
}
