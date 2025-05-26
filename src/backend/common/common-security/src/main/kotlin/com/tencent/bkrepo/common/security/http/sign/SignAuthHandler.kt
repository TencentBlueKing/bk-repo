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

package com.tencent.bkrepo.common.security.http.sign

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.HttpSigner.ACCESS_KEY
import com.tencent.bkrepo.common.service.util.HttpSigner.APP_ID
import com.tencent.bkrepo.common.service.util.HttpSigner.MILLIS_PER_SECOND
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN_BODY
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN_TIME
import com.tencent.bkrepo.common.service.util.HttpSigner.TIME_SPLIT
import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import org.apache.commons.codec.digest.HmacAlgorithms
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

/**
 * 检查签名
 * */
class SignAuthHandler(
    private val authenticationManager: AuthenticationManager,
    private val httpAuthSecurity: HttpAuthSecurity
) : HttpAuthHandler {

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val sig = request.getParameter(SIGN)
        val appId = request.getParameter(APP_ID)
        val accessKey = request.getParameter(ACCESS_KEY)
        if (sig == null || appId == null || accessKey == null) {
            return AnonymousCredentials()
        }
        return SignAuthCredentials(appId, accessKey, sig)
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is SignAuthCredentials)
        /*
        * 普通Body签名，直接读取body，校验md5 // 怎么保证body还能被controller读取
        * 表单Body签名，因为包含文件，所以将除非文件外的表项，添加到query当中统一排序签名，request.parameterMap能直接获取表单参数。
        * */
        val secretKey = authenticationManager.findSecretKey(authCredentials.appId, authCredentials.accessKey)
            // 账号非法
            ?: throw AuthenticationException("AppId or accessKey error.")
        val uri = getUrlPath(request)
        val bodyHash = request.getAttribute(SIGN_BODY).toString()
        val sig = HttpSigner.sign(request, uri, bodyHash, secretKey, HmacAlgorithms.HMAC_SHA_1.getName())
        if (sig != authCredentials.sig) {
            // 签名未通过
            val signatureStr = HttpSigner.getSignatureStr(request, uri, bodyHash)
            throw AuthenticationException("Invalid signature, server signature string: $signatureStr")
        }
        val signTime = request.getParameter(SIGN_TIME)
        val expiredTime = signTime.split(TIME_SPLIT).last().toLong()
        if (expiredTime < System.currentTimeMillis() / MILLIS_PER_SECOND) {
            // 请求超时
            throw PermissionException("Request timeout.")
        }
        return request.getHeader(MS_AUTH_HEADER_UID) ?: SYSTEM_USER
    }

    private fun getUrlPath(request: HttpServletRequest): String {
        val realPath = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
        var path = realPath
        if (httpAuthSecurity.prefixEnabled) {
            path = realPath.removePrefix(httpAuthSecurity.prefix)
        }
        return path
    }

    private data class SignAuthCredentials(
        val appId: String,
        val accessKey: String,
        val sig: String
    ) : HttpAuthCredentials
}
