package com.tencent.bkrepo.replication.config

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.constant.ReplicationMessageCode
import com.tencent.bkrepo.replication.pojo.RemoteClusterInfo
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
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

object FeignClientFactory {

    fun <T> create(target: Class<T>, remoteClusterInfo: RemoteClusterInfo): T {
        return builder.logLevel(Logger.Level.BASIC)
            .logger(loggerFactory.create(target))
            .client(getClient(remoteClusterInfo))
            .encoder(encoder)
            .decoder(decoder)
            .contract(contract)
            .retryer(retryer)
            .options(options)
            .errorDecoder(errorDecoder)
            .target(target, remoteClusterInfo.url)
    }

    private fun getClient(remoteClusterInfo: RemoteClusterInfo): Client {
        return remoteClusterInfo.cert?.let {
            Client.Default(createSSLSocketFactory(it), NoopHostnameVerifier())
        } ?: defaultClient
    }

    private fun createSSLSocketFactory(cert: String): SSLSocketFactory {

        try {
            val decoder = Base64.getDecoder()
            val trustCertBytes = decoder.decode(cert)
            val trustStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(ByteArrayInputStream(trustCertBytes), null)
            val sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, TrustSelfSignedStrategy()).build()
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
    private val defaultClient = Client.Default(null, null)
    private val logger = LoggerFactory.getLogger(FeignClientFactory::class.java)
}
