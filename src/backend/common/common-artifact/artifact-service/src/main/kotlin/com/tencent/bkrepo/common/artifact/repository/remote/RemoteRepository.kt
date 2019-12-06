package com.tencent.bkrepo.common.artifact.repository.remote

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.http.AUTHORIZATION
import com.tencent.bkrepo.common.artifact.repository.http.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.repository.http.PROXY_AUTHORIZATION
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.configuration.ProxyConfiguration
import com.tencent.bkrepo.repository.pojo.repo.configuration.RemoteConfiguration
import com.tencent.bkrepo.repository.pojo.repo.configuration.RemoteCredentialsConfiguration
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.fileupload.servlet.FileCleanerCleanup
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.io.FileCleaningTracker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class RemoteRepository : AbstractArtifactRepository {

    @Autowired
    lateinit var nodeResource: NodeResource
    @Autowired
    lateinit var fileStorage: FileStorage

    override fun onUpload(context: ArtifactUploadContext) {
        throw UnsupportedOperationException()
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        getArtifactCache(context)?.let {
            return it
        }
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = genericRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            val file = createTempFile(context, response.body()!!)
            putArtifactCache(context, file)
            file
        } else null
    }

    /**
     * 尝试读取缓存的远程构件
     */
    private fun getArtifactCache(context: ArtifactDownloadContext): File? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        if (!cacheConfiguration.cacheEnabled) return null

        val artifactInfo = context.artifactInfo
        val node = nodeResource.detail(artifactInfo.projectId, artifactInfo.repoName, artifactInfo.artifactUri).data ?: return null
        if (node.nodeInfo.folder) return null
        val createdDate = LocalDateTime.parse(node.nodeInfo.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val age = Duration.between(createdDate, LocalDateTime.now()).toMinutes()
        return if (age <= cacheConfiguration.cachePeriod) {
            val file = fileStorage.load(node.nodeInfo.sha256!!, context.storageCredentials)
            file?.let { logger.debug("Cached remote artifact[${context.artifactInfo.getFullUri()}] is hit") }
            file
        } else null
    }

    /**
     * 将远程拉取的构件缓存本地
     */
    private fun putArtifactCache(context: ArtifactDownloadContext, file: File) {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        if (cacheConfiguration.cacheEnabled) {
            val repo = context.repositoryInfo
            val nodeCreateRequest = getCacheNodeCreateRequest(context, file)
            val result = nodeResource.create(nodeCreateRequest)
            if (result.isOk()) {
                fileStorage.store(nodeCreateRequest.sha256!!, file.inputStream(), context.storageCredentials)
            } else {
                logger.warn("Cache artifact on [${repo.projectId}/${repo.name}] failed: $result")
            }
        }
    }

    /**
     * 获取缓存节点创建请求
     */
    open fun getCacheNodeCreateRequest(context: ArtifactDownloadContext, file: File): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val sha256 = FileDigestUtils.fileSha256(listOf(file.inputStream()))
        return NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.artifactUri,
            size = file.length(),
            sha256 = sha256,
            overwrite = true,
            operator = context.userId
        )
    }

    /**
     * 创建http client
     */
    private fun createHttpClient(configuration: RemoteConfiguration): OkHttpClient {
        val builder = HttpClientBuilderFactory.create()
        builder.readTimeout(configuration.networkConfiguration.readTimeout, TimeUnit.MILLISECONDS)
        builder.connectTimeout(configuration.networkConfiguration.connectTimeout, TimeUnit.MILLISECONDS)
        builder.proxy(createProxy(configuration.networkConfiguration.proxy))
        builder.proxyAuthenticator(createProxyAuthenticator(configuration.networkConfiguration.proxy))
        builder.authenticator(createAuthenticator(configuration.credentialsConfiguration))

        return builder.build()
    }

    /**
     * 创建代理
     */
    open fun createProxy(configuration: ProxyConfiguration?): Proxy {
        return configuration?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) } ?: Proxy.NO_PROXY
    }

    /**
     * 创建代理身份认证
     */
    open fun createProxyAuthenticator(configuration: ProxyConfiguration?): Authenticator {
        return configuration?.let {
            if (it.username.isNullOrBlank() || it.password.isNullOrBlank()) {
                return Authenticator.NONE
            }
            configuration.password
            Authenticator { _, response ->
                val credential = Credentials.basic(it.username!!, it.password!!)
                response.request().newBuilder().header(PROXY_AUTHORIZATION, credential).build()
            }
        } ?: Authenticator.NONE
    }

    /**
     * 创建身份认证
     */
    open fun createAuthenticator(configuration: RemoteCredentialsConfiguration?): Authenticator {
        return configuration?.let {
            Authenticator { _, response ->
                val credential = Credentials.basic(configuration.username, configuration.password)
                response.request().newBuilder().header(AUTHORIZATION, credential).build()
            }
        } ?: Authenticator.NONE
    }

    /**
     * 生成远程构件下载url
     */
    open fun genericRemoteDownloadUrl(context: ArtifactDownloadContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + artifactUri
    }

    /**
     * 检查下载响应
     */
    open fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download artifact from remote failed: [${response.code()}]")
            return false
        }
        if (response.body()?.contentLength() ?: 0 <= 0) {
            logger.warn("Download artifact from remote failed: response body is empty.")
            return false
        }
        return true
    }

    /**
     * 创建临时文件并将响应体写入文件
     */
    private fun createTempFile(context: ArtifactDownloadContext, body: ResponseBody): File {
        var fileCleaningTracker = context.request.servletContext.getAttribute(FileCleanerCleanup.FILE_CLEANING_TRACKER_ATTRIBUTE) as? FileCleaningTracker
        if (fileCleaningTracker == null) {
            fileCleaningTracker = FileCleaningTracker()
            context.request.servletContext.setAttribute(FileCleanerCleanup.FILE_CLEANING_TRACKER_ATTRIBUTE, fileCleaningTracker)
        }
        // set threshold = 0, guarantee any data will be written to file rather than memory cache
        val artifactFile = ArtifactFileFactory.build(0)
        Streams.copy(body.byteStream(), artifactFile.getOutputStream(), true)
        return artifactFile.getTempFile()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteRepository::class.java)
    }
}
