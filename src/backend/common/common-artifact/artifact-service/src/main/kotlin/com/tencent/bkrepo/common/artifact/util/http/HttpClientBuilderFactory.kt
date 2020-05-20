package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.artifact.util.http.CertTrustManager.disableValidationSSLSocketFactory
import com.tencent.bkrepo.common.artifact.util.http.CertTrustManager.disableValidationTrustManager
import com.tencent.bkrepo.common.artifact.util.http.CertTrustManager.trustAllHostname
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 *
 * @author: carrypan
 * @date: 2019/12/3
 */
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
            }
    }
}
