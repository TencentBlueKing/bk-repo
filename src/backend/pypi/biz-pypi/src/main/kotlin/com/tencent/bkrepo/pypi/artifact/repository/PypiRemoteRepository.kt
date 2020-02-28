package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.http.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.stereotype.Component
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiRemoteRepository : RemoteRepository(), PypiRepository {

    override fun generateRemoteDownloadUrl(context: ArtifactDownloadContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + "/packages" + artifactUri
    }

    /**
     * 生成远程list url
     */
    fun generateRemoteListUrl(context: ArtifactListContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + "/simple$artifactUri"
    }

    override fun list(context: ArtifactListContext) {
        val listUri = generateRemoteListUrl(context)
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/html; charset=UTF-8"
        val build: Request = Request.Builder().get().url(listUri).build()
        val htmlContent: String? = okHttpClient.newCall(build).execute().body()?.string()
        response.writer.print(htmlContent)
    }

    override fun searchXml(context: ArtifactSearchContext, xmlString: String) {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpsClient(remoteConfiguration)
        val response = HttpContextHolder.getResponse()
        val body = RequestBody.create(MediaType.parse("text/xml"), xmlString)
        response.contentType = "text/xml; charset=UTF-8"
        val build: Request = Request.Builder().url(remoteConfiguration.url)
            .addHeader("Accept-Encoding", "gzip, deflate")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
            .post(body)
            .build()
        val htmlContent: String? = okHttpClient.newCall(build).execute().body()?.string()
        response.writer.print(htmlContent)
    }

    private fun createHttpsClient(configuration: RemoteConfiguration): OkHttpClient {
        val builder = HttpClientBuilderFactory.create()
        builder.readTimeout(configuration.networkConfiguration.readTimeout, TimeUnit.MILLISECONDS)
        builder.connectTimeout(configuration.networkConfiguration.connectTimeout, TimeUnit.MILLISECONDS)
        builder.proxy(createProxy(configuration.networkConfiguration.proxy))
        builder.proxyAuthenticator(createProxyAuthenticator(configuration.networkConfiguration.proxy))
        builder.authenticator(createAuthenticator(configuration.credentialsConfiguration))
        builder.sslSocketFactory(sslSocketFactory(), trustAllCerts[0] as X509TrustManager)

        return builder.build()
    }

    private fun sslSocketFactory(): SSLSocketFactory {
        try {
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

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            return sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
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
