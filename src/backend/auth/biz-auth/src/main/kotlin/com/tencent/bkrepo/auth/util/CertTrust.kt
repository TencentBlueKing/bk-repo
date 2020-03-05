package com.tencent.bkrepo.auth.util

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Arrays
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object CertTrust {

    public lateinit var client: OkHttpClient

    @Throws(Exception::class)
    fun call(addr: String): String {
        val request = Request.Builder()
            .url(addr)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseHeaders = response.headers()
            for (i in 0 until responseHeaders.size()) {
                println(responseHeaders.name(i) + ": " + responseHeaders.value(i))
            }
            return response.body()!!.string()
        }
    }

    private fun trustedCertificatesInputStream(cert: String): InputStream {
        return Buffer()
            .writeUtf8(cert)
            .inputStream()
    }

    @Throws(GeneralSecurityException::class)
    private fun trustManagerForCertificates(input: InputStream): X509TrustManager {
        val certificateFactory =
            CertificateFactory.getInstance("X.509")
        val certificates =
            certificateFactory.generateCertificates(input)
        require(!certificates.isEmpty()) { "expected non-empty set of trusted certificates" }
        // Put the certificates a key store.
        val password = "password".toCharArray() // Any password will work.
        val keyStore = newEmptyKeyStore(password)
        var index = 0
        for (certificate in certificates) {
            val certificateAlias = Integer.toString(index++)
            keyStore.setCertificateEntry(certificateAlias, certificate)
        }
        // Use it to build an X509 trust manager.
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        )
        keyManagerFactory.init(keyStore, password)
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)
        val trustManagers = trustManagerFactory.trustManagers
        check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
            ("Unexpected default trust managers:" +
                Arrays.toString(trustManagers))
        }
        return trustManagers[0] as X509TrustManager
    }

    @Throws(GeneralSecurityException::class)
    private fun newEmptyKeyStore(password: CharArray): KeyStore {
        return try {
            val keyStore =
                KeyStore.getInstance(KeyStore.getDefaultType())
            val input: InputStream? = null // By convention, 'null' creates an empty key store.
            keyStore.load(input, password)
            keyStore
        } catch (e: IOException) {
            throw AssertionError(e)
        }
    }

    public fun initClient(cert: String) {
        val trustManager: X509TrustManager
        val sslSocketFactory: SSLSocketFactory
        try {
            trustManager = trustManagerForCertificates(trustedCertificatesInputStream(cert))
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            sslSocketFactory = sslContext.socketFactory
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .build()
    }
}
