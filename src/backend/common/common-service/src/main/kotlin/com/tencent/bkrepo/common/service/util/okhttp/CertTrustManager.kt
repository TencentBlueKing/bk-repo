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

package com.tencent.bkrepo.common.service.util.okhttp

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * SSL证书管理器
 */
object CertTrustManager {
    private val logger: Logger = LoggerFactory.getLogger(CertTrustManager::class.java)
    private const val TLS = "TLS"
    private const val X509 = "X.509"

    val disableValidationTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // no-op
        }
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // no-op
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val trustAllHostname = HostnameVerifier { _, _ -> true }
    val disableValidationSSLSocketFactory = createSSLSocketFactory(disableValidationTrustManager)

    fun createSSLSocketFactory(certString: String): SSLSocketFactory {
        val trustManager = createTrustManager(certString)
        return createSSLSocketFactory(trustManager)
    }

    fun createSSLSocketFactory(trustManager: TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance(TLS).apply { init(null, arrayOf(trustManager), null) }
        return sslContext.socketFactory
    }

    fun createTrustManager(certString: String): X509TrustManager {
        val certInputStream = certString.byteInputStream(Charsets.UTF_8)
        val certificateFactory = CertificateFactory.getInstance(X509)
        val certificate = certificateFactory.generateCertificate(certInputStream)
        try {
            // 校验传入的证书是否过期或者无效
            (certificate as X509Certificate).checkValidity()
        } catch (e: Exception) {
            logger.error("The certificate $certString is currently invalid, $e")
            return disableValidationTrustManager
        }
        return CustomX509TrustManager(certificate)
    }

    class CustomX509TrustManager(
        private val localCertificate: X509Certificate
    ): X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            // 校验服务端证书
            if (chain.isNullOrEmpty()) {
                throw IllegalArgumentException("Server certificate chain is empty");
            }
            // 校验证书链的第一个证书是否与本地证书一致
            val serverCert = chain[0]
            // 可能存在证书没有过期的情况下在服务端已经被替换
            if (localCertificate != serverCert) {
                logger.error("The localCertificate ${localCertificate.subjectDN} is not equal with serverCert ${serverCert.subjectDN}")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            // 返回本地证书
            return arrayOf(localCertificate)
        }
    }
}
