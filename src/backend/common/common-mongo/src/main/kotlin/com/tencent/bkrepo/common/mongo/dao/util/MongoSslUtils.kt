package com.tencent.bkrepo.common.mongo.dao.util

import com.tencent.bkrepo.common.mongo.properties.MongoSslProperties
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

object MongoSslUtils {
    /**
     * 创建单向TLS的SSLContext
     */
    @Throws(Exception::class)
    fun createOnewayTlsSslContext(sslProps: MongoSslProperties): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, getTrustManagers(sslProps), SecureRandom())
        }
    }

    /**
     * 创建双向TLS的SSLContext
     */
    @Throws(Exception::class)
    fun createMutualTlsSslContext(sslProps: MongoSslProperties): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(getKeyManagers(sslProps), getTrustManagers(sslProps), SecureRandom())
        }
    }

    @Throws(Exception::class)
    private fun getTrustManagers(sslProps: MongoSslProperties): Array<TrustManager> {
        return if (!sslProps.trustStore.isNullOrBlank()) {
            logger.info("Loading mongo client trust store: ${sslProps.trustStore}")

            val trustStore = KeyStore.getInstance(sslProps.trustStoreType).apply {
                FileInputStream(sslProps.trustStore!!).use { fis ->
                    load(fis, sslProps.trustStorePassword?.toCharArray())
                }
            }

            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trustStore)
            }.trustManagers
        } else {
            // fallback至JVM默认信任库
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }.trustManagers
        }
    }

    @Throws(Exception::class)
    private fun getKeyManagers(sslProps: MongoSslProperties): Array<KeyManager> {
        logger.info("Loading mongo client key store: ${sslProps.keyStore}")

        val keyStore = KeyStore.getInstance(sslProps.keyStoreType).apply {
            FileInputStream(sslProps.keyStore!!).use { fis ->
                load(fis, sslProps.keyStorePassword?.toCharArray())
            }
        }

        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, sslProps.keyStorePassword?.toCharArray())
        }.keyManagers
    }

    private val logger = LoggerFactory.getLogger(MongoSslUtils::class.java)
}