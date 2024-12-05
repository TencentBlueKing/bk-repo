/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.utils

import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * SSL工具
 */
object SslUtils {

    /**
     * 忽略所有HTTPS请求的SSL证书
     * 必须在openConnection之前调用
     */
    @Throws(Exception::class)
    fun ignoreSsl() {
        val hv = HostnameVerifier { _, _ -> true }
        trustAllHttpsCertificates()
        HttpsURLConnection.setDefaultHostnameVerifier(hv)
    }

    /**
     * 信任所有HTTPS证书
     */
    @Throws(Exception::class)
    private fun trustAllHttpsCertificates() {
        val trustAllCerts = arrayOf<TrustManager>(miTM())
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, null)
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
    }

    // 自定义 TrustManager 实现
    private class miTM : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate>? = null

        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}

        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
    }
}