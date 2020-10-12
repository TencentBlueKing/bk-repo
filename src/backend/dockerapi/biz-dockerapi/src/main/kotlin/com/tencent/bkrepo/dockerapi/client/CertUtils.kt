package com.tencent.bkrepo.dockerapi.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object CertUtils {
    fun createSSLSocketFactory(): SSLSocketFactory {
        var ssLSocketFactory: SSLSocketFactory? = null
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, arrayOf(TrustAllManager()), SecureRandom())
            ssLSocketFactory = sc.socketFactory
        } catch (e: Exception) {
            // never happen
        }
        return ssLSocketFactory!!
    }
}

class TrustAllHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        return true
    }
}

class TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(certChain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
    }
}

