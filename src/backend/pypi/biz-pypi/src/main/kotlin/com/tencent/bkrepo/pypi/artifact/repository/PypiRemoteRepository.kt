package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.http.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.pypi.artifact.FLUSH_CACHE_EXPIRE
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.REMOTE_HTML_CACHE_FULL_PATH
import com.tencent.bkrepo.pypi.artifact.SSL_PORT
import com.tencent.bkrepo.pypi.artifact.XML_RPC_URI
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.security.cert.CertificateException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiRemoteRepository::class.java)
    }

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
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/html; charset=UTF-8"
        val cacheHtml = getCacheHtml(context)
        val htmlContext = StringBuilder()
        cacheHtml?.let {
            BufferedReader(FileReader(cacheHtml)).use {
                var line: String
                while (true) {
                    line = it.readLine() ?: break
                    htmlContext.append(line)
                }
            }
        }
        response.writer.print(htmlContext.toString())
    }

    /**
     * 获取项目-仓库缓存对应的html文件
     */
    fun getCacheHtml(context: ArtifactListContext): File? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = REMOTE_HTML_CACHE_FULL_PATH
        val node = nodeResource.detail(projectId, repoName, fullPath).data
        while (node == null) {
            cacheRemoteRepoList(context)
            Thread.sleep(60)
        }
        node.nodeInfo.takeIf { !it.folder } ?: return null
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SS")
        val date = LocalDateTime.parse(node.nodeInfo.lastModifiedDate, format)
        val currentTime = LocalDateTime.now()
        val duration = Duration.between(date, currentTime).toMinutes()
        while (duration > FLUSH_CACHE_EXPIRE) {
            var job = GlobalScope.launch {
                cacheRemoteRepoList(context)
            }

        }

        return storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
    }

    /**
     * 缓存html文件
     */
    fun cacheRemoteRepoList(context: ArtifactListContext) {
        val listUri = generateRemoteListUrl(context)
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/html; charset=UTF-8"
        val build: Request = Request.Builder().get().url(listUri).build()
        val htmlContent = okHttpClient.newCall(build).execute().body()?.string()
        val cacheHtmlFile = File(REMOTE_HTML_CACHE_FULL_PATH)
        htmlContent?.let {
            // 保存html文件
            try {
                val fileWriter = FileWriter(cacheHtmlFile)
                fileWriter.write(htmlContent)
            } catch (ioe: IOException) {
            }
        }
        onUpload(context, cacheHtmlFile)
    }

    fun onUpload(context: ArtifactListContext, file: File) {
        val nodeCreateRequest = getNodeCreateRequest(context, file)
        nodeResource.create(nodeCreateRequest)
        storageService.store(nodeCreateRequest.sha256!!, file, context.storageCredentials)
    }

    /**
     * 获取PYPI节点创建请求,保存html文件
     */
    fun getNodeCreateRequest(context: ArtifactListContext, file: File): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val fileInputStream01 = FileInputStream(file)
        val sha256 = FileDigestUtils.fileSha256(fileInputStream01)
        val fileInputStream02 = FileInputStream(file)
        val md5 = FileDigestUtils.fileMd5(fileInputStream02)
        val pypiArtifactInfo = artifactInfo as PypiArtifactInfo

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            overwrite = true,
            fullPath = "/$REMOTE_HTML_CACHE_FULL_PATH",
            size = file.length(),
            sha256 = sha256 as String?,
            md5 = md5 as String?,
            operator = context.userId,
            metadata = pypiArtifactInfo.metadata
        )
    }

    override fun searchXml(context: ArtifactSearchContext, xmlString: String) {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpsClient(remoteConfiguration)
        val response = HttpContextHolder.getResponse()
        val body = RequestBody.create(MediaType.parse("text/xml"), xmlString)
        response.contentType = "text/xml; charset=UTF-8"
        val remoteUrl = removeSlash(remoteConfiguration.url)
        val build: Request = Request.Builder().url("$remoteUrl:$SSL_PORT/$XML_RPC_URI")
            .addHeader("Connection", "keep-alive")
            .post(body)
            .build()
        val htmlContent: String? = okHttpClient.newCall(build).execute().body()?.string()
        response.writer.print(htmlContent)
    }

    private fun removeSlash(url: String): String {
        if (url.endsWith("/")) {
            return url.substring(0, url.length - 1)
        }
        return url
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
