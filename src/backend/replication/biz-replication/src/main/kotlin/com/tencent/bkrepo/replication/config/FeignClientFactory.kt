package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.constant.ReplicationMessageCode
import feign.Client
import feign.Contract
import feign.Feign
import feign.Logger
import feign.Request
import feign.Retryer
import feign.codec.Decoder
import feign.codec.Encoder
import feign.codec.ErrorDecoder
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory
import org.springframework.cloud.openfeign.FeignLoggerFactory
import sun.misc.BASE64Decoder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.SSLSocketFactory

object FeignClientFactory {

    fun <T> create(target: Class<T>, url: String): T {
        return builder.logLevel(Logger.Level.BASIC)
            .logger(loggerFactory.create(target))
            //.client(client)
            .encoder(encoder)
            .decoder(decoder)
            .contract(contract)
            .retryer(retryer)
            .options(options)
            .errorDecoder(errorDecoder)
            .target(target, url)
    }

    private fun client(): Client {
        return Client.Default(getSSLSocketFactory(), NoopHostnameVerifier())
    }

    private fun getSSLSocketFactory(): SSLSocketFactory {
        val decoder = BASE64Decoder()
        val keyStoreInput: InputStream?
        val trustStoreInput: InputStream?
        val keyStoreBaseStr = "keyStore"
        val userIdBaseStr = "trustStore"
        val password = "123456"
        try {
            val keyStoreBytes = decoder.decodeBuffer(keyStoreBaseStr)
            val trustStoreBytes = decoder.decodeBuffer(userIdBaseStr)
            val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStoreInput = ByteArrayInputStream(keyStoreBytes)
            keyStore.load(keyStoreInput, password.toCharArray())
            val trustStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStoreInput = ByteArrayInputStream(trustStoreBytes)
            trustStore.load(trustStoreInput, null)
            val sslContext = SSLContexts.custom().loadKeyMaterial(keyStore, password.toCharArray())
                .loadTrustMaterial(trustStore, TrustSelfSignedStrategy()).build()
            return sslContext.socketFactory
        } catch (exception: Exception) {
            logger.error("Create SSLSocketFactory error.", exception)
            throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_SSL_ERROR)
        }
    }

    private val builder = SpringContextUtils.getBean(Feign.Builder::class.java)
    private val loggerFactory = SpringContextUtils.getBean(FeignLoggerFactory::class.java)
    private val encoder = SpringContextUtils.getBean(Encoder::class.java)
    private val decoder = SpringContextUtils.getBean(Decoder::class.java)
    private val contract = SpringContextUtils.getBean(Contract::class.java)
    private val retryer = SpringContextUtils.getBean(Retryer::class.java)
    private val errorDecoder = SpringContextUtils.getBean(ErrorDecoder::class.java)
    // 设置不超时
    private val options = Request.Options(10 * 1000, 0)
    //private val client = client()

    private val logger = LoggerFactory.getLogger(FeignClientFactory::class.java)
}
