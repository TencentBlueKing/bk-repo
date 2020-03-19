package com.tencent.bkrepo.common.artifact.repository.http

import okhttp3.OkHttpClient
import org.springframework.cloud.commons.httpclient.OkHttpClientFactory
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/**
 *
 * @author: carrypan
 * @date: 2019/12/3
 */
object HttpClientBuilderFactory {

    private const val TLS = "TLS"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(createSslSocketFactory(), trustAllCertsManager)
            .hostnameVerifier(trustAllHostnameVerifier)
            .build()
    }

    fun create(): OkHttpClient.Builder = okHttpClient.newBuilder()

    private fun createSslSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance(TLS)
        sslContext.init(null, arrayOf(trustAllCertsManager), SecureRandom())
        return sslContext.socketFactory
    }

    private val trustAllCertsManager = OkHttpClientFactory.DisableValidationTrustManager()
    private val trustAllHostnameVerifier = OkHttpClientFactory.TrustAllHostnames()
}
