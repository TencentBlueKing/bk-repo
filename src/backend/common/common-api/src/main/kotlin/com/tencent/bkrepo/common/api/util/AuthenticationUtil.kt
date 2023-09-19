/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.api.util

import com.tencent.bkrepo.common.api.constant.AuthenticationKeys.BEARER_REALM
import com.tencent.bkrepo.common.api.constant.AuthenticationKeys.SCOPE
import com.tencent.bkrepo.common.api.constant.AuthenticationKeys.SERVICE
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.authentication.AuthenticationProperty

object AuthenticationUtil {

    /**
     * 解析返回头中的WWW_AUTHENTICATE字段， 只针对为Bearer realm
     */
    fun parseWWWAuthenticateHeader(wwwAuthenticate: String, scope: String?): AuthenticationProperty? {
        val map: MutableMap<String, String> = mutableMapOf()
        return try {
            val params = wwwAuthenticate.split("\",")
            params.forEach {
                val param = it.split(Regex("="),2)
                val name = param.first()
                val value = param.last().replace("\"", "")
                map[name] = value
            }
            AuthenticationProperty(
                authUrl = map[BEARER_REALM]!!,
                service = map[SERVICE]!!,
                scope = scope
            )
        } catch (e: Exception) {
            null
        }
    }

    fun buildAuthenticationUrl(property: AuthenticationProperty, userName: String?): String? {
        if (property.authUrl.isBlank()) return null
        var result = if (property.authUrl.contains(StringPool.QUESTION)) {
            "${property.authUrl}&$SERVICE=${property.service}"
        } else {
            "${property.authUrl}?$SERVICE=${property.service}"
        }
        property.scope?.let {
            result += "&$SCOPE=${property.scope}"
        }
        userName?.let { result += "&account=$userName" }
        return result
    }
}