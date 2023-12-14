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

package com.tencent.bkrepo.replication.replica.repository.remote.type.oci

import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.AuthenticationUtil.buildAuthenticationUrl
import com.tencent.bkrepo.common.api.util.AuthenticationUtil.parseWWWAuthenticateHeader
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.replication.pojo.docker.OciResponse
import com.tencent.bkrepo.replication.pojo.remote.BearerToken
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.repository.remote.base.AuthorizationService
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.util.HttpUtils
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMethod

/**
 * 针对oci的Authorization code获取实现
 */
@Component
class OciAuthorizationService : AuthorizationService {
    override fun obtainAuthorizationCode(property: RequestProperty?, httpClient: OkHttpClient): String? {
        if (property == null) return null
        val authorizationCode = property.authorizationCode
        val authProperty = property.copy(authorizationCode = null)
        val httpRequest = HttpUtils.wrapperRequest(authProperty)
        httpClient.newCall(httpRequest).execute().use {
            return if (it.code == HttpStatus.UNAUTHORIZED.value) {
                getAuthenticationCode(
                    response = it,
                    authorizationCode = authorizationCode,
                    userName = property.userName,
                    scope = property.scope,
                    httpClient = httpClient
                )
                // 当无需鉴权时返回""
            } else if (it.code == HttpStatus.OK.value) {
                StringPool.EMPTY
            } else throw ArtifactPushException(
                "Can not get authorization detail ${it.code}, please check your distribution host"
            )
        }
    }

    /**
     * 根据返回头中的WWW_AUTHENTICATE字段，获取token
     */
    private fun getAuthenticationCode(
        response: Response,
        scope: String? = null,
        userName: String? = null,
        authorizationCode: String? = null,
        httpClient: OkHttpClient
    ): String {
        val wwwAuthenticate = response.header(HttpHeaders.WWW_AUTHENTICATE)
        if (wwwAuthenticate.isNullOrBlank() || !wwwAuthenticate.startsWith(BEARER_AUTH_PREFIX)) {
            throw ArtifactPushException("Auth url can not be parsed from header.")
        }
        val authProperty = parseWWWAuthenticateHeader(wwwAuthenticate, scope)
            ?: throw ArtifactPushException("Auth url can not be parsed from header.")
        val urlStr = buildAuthenticationUrl(authProperty, userName)
        val token = userName?.let {
            authorizationCode
        }
        val property = RequestProperty(
            requestMethod = RequestMethod.GET,
            requestUrl = urlStr,
            authorizationCode = token,
        )
        val httpRequest = HttpUtils.wrapperRequest(property)
        httpClient.newCall(httpRequest).execute().use {
            if (!it.isSuccessful) {
                val error = try {
                    JsonUtils.objectMapper.readValue(it.body!!.byteStream(), OciResponse::class.java).toJsonString()
                } catch (ignore: Exception) {
                    StringPool.EMPTY
                }
                throw ArtifactPushException(
                    "Could not get token from auth service," +
                        " code is ${it.code} and response is $error"
                )
            }
            try {
                val bearerToken = JsonUtils.objectMapper.readValue(it.body!!.byteStream(), BearerToken::class.java)
                return "Bearer ${bearerToken.token}"
            } catch (e: Exception) {
                throw ArtifactPushException(
                    "Could not get token from auth service, please check your distribution address!"
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciAuthorizationService::class.java)
    }
}
