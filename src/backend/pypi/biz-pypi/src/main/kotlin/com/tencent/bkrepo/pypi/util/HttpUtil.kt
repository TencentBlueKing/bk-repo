package com.tencent.bkrepo.pypi.util

import java.security.cert.CertificateException
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HttpUtil {
    fun hasProtocol(url: String): Boolean {
        val pattern = Pattern.compile("(http[s]?://)([-.a-z0-9A-Z]+)([/]?.*)")
        return pattern.matcher(url).matches()
    }

    private fun removeSlash(url: String): String {
        if (url.endsWith("/")) {
            return url.substring(0, url.length - 1)
        }
        return url
    }

    fun sslSocketFactory(): SSLSocketFactory {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            return sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
            return arrayOf()
        }
    })
}
