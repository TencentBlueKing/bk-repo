package com.tencent.bkrepo.common.artifact.util.okhttp

import com.tencent.bkrepo.common.artifact.util.okhttp.CertTrustManager.disableValidationSSLSocketFactory
import com.tencent.bkrepo.common.artifact.util.okhttp.CertTrustManager.disableValidationTrustManager
import com.tencent.bkrepo.common.artifact.util.okhttp.CertTrustManager.trustAllHostname
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientBuilderFactory {

    private const val DEFAULT_READ_TIMEOUT_SECONDS = 10 * 1000L
    private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10 * 1000L

    private val defaultClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(disableValidationSSLSocketFactory, disableValidationTrustManager)
            .hostnameVerifier(trustAllHostname)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun create(certificate: String? = null, neverReadTimeout: Boolean = false): OkHttpClient.Builder {
        return defaultClient.newBuilder()
            .apply {
                certificate?.let {
                    val trustManager = CertTrustManager.createTrustManager(it)
                    val sslSocketFactory = CertTrustManager.createSSLSocketFactory(trustManager)
                    sslSocketFactory(sslSocketFactory, trustManager)
                }
            }.apply {
                if (neverReadTimeout) {
                    readTimeout(0, TimeUnit.MILLISECONDS)
                }
            }.apply {
                writeTimeout(0, TimeUnit.MILLISECONDS)
            }
    }
}
