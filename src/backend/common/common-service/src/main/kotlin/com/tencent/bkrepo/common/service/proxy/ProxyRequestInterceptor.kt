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

package com.tencent.bkrepo.common.service.proxy

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.PROXY_HEADER_NAME
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.constant.urlEncode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.HttpSigner
import feign.RequestInterceptor
import feign.RequestTemplate
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Response
import okio.Buffer
import org.apache.commons.codec.digest.HmacAlgorithms
import org.springframework.http.MediaType

class ProxyRequestInterceptor: RequestInterceptor, Interceptor {
    override fun apply(template: RequestTemplate) {
        val projectId = ProxyEnv.getProjectId()
        val name = ProxyEnv.getName()
        template.header(PROXY_HEADER_NAME, name)
        HeaderUtils.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let { lang ->
            template.header(HttpHeaders.ACCEPT_LANGUAGE, lang)
        }
        HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.let { userId ->
            template.header(MS_AUTH_HEADER_UID, userId.toString())
        } ?: template.header(MS_AUTH_HEADER_UID, "system")

        val sessionKey: String
        try {
            sessionKey = SessionKeyHolder.getSessionKey()
        } catch (e: ErrorCodeException) {
            // 获取不到sessionKey时的请求不需要签名
            return
        }
        // 不要使用feign请求来上传文件，所以这里不存在文件请求，可以完全读取body进行签名
        val bodyToHash = if (template.body() != null && template.body().isNotEmpty()) {
            template.body()
        } else {
            StringPool.EMPTY.toByteArray()
        }
        val algorithm = HmacAlgorithms.HMAC_SHA_1.getName()
        val startTime = System.currentTimeMillis() / HttpSigner.MILLIS_PER_SECOND
        val endTime = startTime + HttpSigner.REQUEST_TTL
        template.query(HttpSigner.PROXY_NAME, name)
            .query(HttpSigner.PROJECT_ID, projectId)
            .query(HttpSigner.SIGN_TIME, "$startTime${HttpSigner.TIME_SPLIT}$endTime".urlEncode())
            .query(HttpSigner.SIGN_ALGORITHM, algorithm)
        val bodyHash = Hashing.sha256().hashBytes(bodyToHash).toString()
        val sig = HttpSigner.sign(template, bodyHash, sessionKey, algorithm)
        template.query(HttpSigner.SIGN, sig)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val projectId = ProxyEnv.getProjectId()
        val name = ProxyEnv.getName()
        val sessionKey: String
        try {
            sessionKey = SessionKeyHolder.getSessionKey()
        } catch (e: ErrorCodeException) {
            // 获取不到sessionKey时的请求不需要签名
            return chain.proceed(request)
        }
        val startTime = System.currentTimeMillis() / HttpSigner.MILLIS_PER_SECOND
        var endTime = startTime + HttpSigner.REQUEST_TTL
        val urlBuilder = request.url.newBuilder()
        val body = request.body
        /*
        * 文件请求使用multipart/form-data，为避免读取文件，这里使用空串。表单参数应该包含文件的sha256。
        * 通过对表单参数的签名，来实现对文件请求的签名。
        * */
        val bodyToHash = if (body != null && body !is MultipartBody &&
            request.body?.contentType().toString() != MediaType.APPLICATION_OCTET_STREAM_VALUE) {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } else {
            // 文件请求TTL不限制
            endTime = startTime + Int.MAX_VALUE
            StringPool.EMPTY.toByteArray()
        }
        // 添加签名必要参数
        urlBuilder.addQueryParameter(HttpSigner.PROXY_NAME, name)
            .addQueryParameter(HttpSigner.PROJECT_ID, projectId)
            .addQueryParameter(HttpSigner.SIGN_TIME, "$startTime${HttpSigner.TIME_SPLIT}$endTime")
        val newRequest = request.newBuilder().url(urlBuilder.build())
            .addHeader(PROXY_HEADER_NAME, name)
            .apply {
                HeaderUtils.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let { lang ->
                    addHeader(HttpHeaders.ACCEPT_LANGUAGE, lang)
                }
                HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.let { userId ->
                    addHeader(MS_AUTH_HEADER_UID, userId.toString())
                } ?: addHeader(MS_AUTH_HEADER_UID, "system")
            }
            .build()
        val bodyHash = Hashing.sha256().hashBytes(bodyToHash).toString()
        val realPath = request.url.toUri().path
        val signPath = if (realPath.startsWith("/$SERVICE_NAME")) {
            realPath.removePrefix("/$SERVICE_NAME")
        } else {
            realPath
        }
        val sig = HttpSigner.sign(newRequest, signPath, bodyHash, sessionKey, HmacAlgorithms.HMAC_SHA_1.getName())
        urlBuilder.addQueryParameter(HttpSigner.SIGN, sig)
        val newRequest2 = newRequest.newBuilder().url(urlBuilder.build()).build()
        return chain.proceed(newRequest2)
    }

    companion object {
        private const val SERVICE_NAME = "replication"
    }


}
