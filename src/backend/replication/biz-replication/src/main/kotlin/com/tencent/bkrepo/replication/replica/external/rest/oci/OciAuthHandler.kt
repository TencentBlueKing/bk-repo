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

package com.tencent.bkrepo.replication.replica.external.rest.oci

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.security.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.security.util.BasicAuthUtils
import com.tencent.bkrepo.replication.constant.BEARER_REALM
import com.tencent.bkrepo.replication.constant.GET_METHOD
import com.tencent.bkrepo.replication.constant.METHOD
import com.tencent.bkrepo.replication.constant.PASSWORD
import com.tencent.bkrepo.replication.constant.REPOSITORY
import com.tencent.bkrepo.replication.constant.SCOPE
import com.tencent.bkrepo.replication.constant.SERVICE
import com.tencent.bkrepo.replication.constant.URL
import com.tencent.bkrepo.replication.constant.USERNAME
import com.tencent.bkrepo.replication.pojo.deploy.BearerToken
import com.tencent.bkrepo.replication.replica.external.exception.RepoDeployException
import com.tencent.bkrepo.replication.replica.external.rest.base.AuthHandler
import com.tencent.bkrepo.replication.util.HttpUtils
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 权限处理类
 */
class OciAuthHandler(
    private val httpClient: OkHttpClient
) : AuthHandler() {

    /**
     * 获取token
     */
    override fun obtainToken(extraMap: Map<String, Any>?): String {
        val httpRequest = HttpUtils.wrapperRequest(originalMap = this.map)
        httpClient.newCall(httpRequest).execute().use {
            return if (it.code() == HttpStatus.UNAUTHORIZED.value) {
                getAuthenticationCode(it)
            } else if (it.code() == HttpStatus.OK.value) {
                StringPool.EMPTY
            } else throw RepoDeployException("Can not get auth info ${it.code()}")
        }
    }

    /**
     * 根据返回头中的WWW_AUTHENTICATE字段，获取token
     */
    private fun getAuthenticationCode(
        response: Response
    ): String {
        val wwwAuthenticate = response.header(HttpHeaders.WWW_AUTHENTICATE)
        if (wwwAuthenticate.isNullOrBlank() || !wwwAuthenticate.startsWith(BEARER_AUTH_PREFIX)) {
            throw RepoDeployException("Auth url can not be parsed from header.")
        }
        val url = parseWWWAuthenticateHeader(wwwAuthenticate)
        logger.info("The url for authenticating is $url")
        if (url.isNullOrEmpty()) throw RepoDeployException("Auth url can not be parsed from header.")
        val urlStr = "$url&account=${this.map[USERNAME]}"
        val extraMap = mutableMapOf<String, Any>()
        extraMap[URL] = urlStr
        extraMap[METHOD] = GET_METHOD
        val token = if (this.map[USERNAME] != null) {
            BasicAuthUtils.encode(this.map[USERNAME] as String, this.map[PASSWORD] as String)
        } else null
        val httpRequest = HttpUtils.wrapperRequest(token = token, extraMap = extraMap)
        val tokenResponse = httpClient.newCall(httpRequest).execute()
        try {
            if (!tokenResponse.isSuccessful) throw RepoDeployException("Could not get token from auth service")
            val input = tokenResponse.body()!!.byteStream()
            val bearerToken = JsonUtils.objectMapper.readValue(input, BearerToken::class.java)
            return "Bearer ${bearerToken.token}"
        } finally {
            tokenResponse.body()?.close()
        }
    }

    /**
     * 解析返回头中的WWW_AUTHENTICATE字段， 只针对为Bearer realm
     */
    private fun parseWWWAuthenticateHeader(wwwAuthenticate: String): String? {
        val map: MutableMap<String, String> = mutableMapOf()
        return try {
            val params = wwwAuthenticate.split("\",")
            params.forEach {
                val param = it.split("=")
                val name = param.first()
                val value = param.last().replace("\"", "")
                map[name] = value
            }
            map[SCOPE]?.let {
                val repo = map[SCOPE]!!.split(":").first()
                val scope = map[SCOPE]!!.split(":").last()
                map[SCOPE] = repo + StringPool.COLON + this.map[REPOSITORY].toString() + StringPool.COLON + scope
            }
            "${map[BEARER_REALM]}?$SERVICE=${map[SERVICE]}&$SCOPE=${map[SCOPE]}"
        } catch (e: Exception) {
            logger.warn("Parsing wwwAuthenticate header error: ${e.message}")
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciAuthHandler::class.java)
    }
}
