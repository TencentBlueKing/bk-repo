package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.http.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.pypi.artifact.FLUSH_CACHE_EXPIRE
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.REMOTE_HTML_CACHE_FULL_PATH
import com.tencent.bkrepo.pypi.artifact.XML_RPC_URI
import com.tencent.bkrepo.pypi.artifact.xml.Value
import com.tencent.bkrepo.pypi.artifact.xml.XmlConvertUtil
import com.tencent.bkrepo.pypi.util.HttpUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiRemoteRepository : RemoteRepository(), PypiRepository {

    override fun generateRemoteDownloadUrl(context: ArtifactTransferContext): String {
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
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        val date = LocalDateTime.parse(node.nodeInfo.lastModifiedDate, format)
        val currentTime = LocalDateTime.now()
        val duration = Duration.between(date, currentTime).toMinutes()
        var job = GlobalScope.launch {
            while (duration > FLUSH_CACHE_EXPIRE) {
                cacheRemoteRepoList(context)
            }
        }
        job.start()
        return storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
    }

    /**
     * 缓存html文件
     */
    fun cacheRemoteRepoList(context: ArtifactListContext) {
        val listUri = generateRemoteListUrl(context)
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
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
        onDelete(context, cacheHtmlFile)
        onUpload(context, cacheHtmlFile)
    }

    fun onUpload(context: ArtifactListContext, file: File) {
        val nodeCreateRequest = getNodeCreateRequest(context, file)
        nodeResource.create(nodeCreateRequest)
        storageService.store(nodeCreateRequest.sha256!!, file, context.storageCredentials)
    }
    fun onDelete(context: ArtifactListContext, file: File) {
        val nodeCreateRequest = getNodeCreateRequest(context, file)
        nodeResource.create(nodeCreateRequest)
        storageService.delete(nodeCreateRequest.sha256!!, context.storageCredentials)
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

    override fun searchNodeList(context: ArtifactSearchContext, xmlString: String): MutableList<Value>? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val okHttpClient: OkHttpClient = createHttpsClient(remoteConfiguration)
        val body = RequestBody.create(MediaType.parse("text/xml"), xmlString)
        val build: Request = Request.Builder().url("${remoteConfiguration.url}$XML_RPC_URI")
            .addHeader("Connection", "keep-alive")
            .post(body)
            .build()
        val htmlContent: String? = okHttpClient.newCall(build).execute().body()?.string()
        htmlContent?.let {
            try {
                val methodResponse = XmlConvertUtil.xml2MethodResponse(it)
                return methodResponse.params.paramList[0].value.array?.data?.valueList
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun createHttpsClient(configuration: RemoteConfiguration): OkHttpClient {
        val builder = HttpClientBuilderFactory.create()
        builder.readTimeout(configuration.networkConfiguration.readTimeout, TimeUnit.MILLISECONDS)
        builder.connectTimeout(configuration.networkConfiguration.connectTimeout, TimeUnit.MILLISECONDS)
        builder.proxy(createProxy(configuration.networkConfiguration.proxy))
        builder.proxyAuthenticator(createProxyAuthenticator(configuration.networkConfiguration.proxy))
        builder.authenticator(createAuthenticator(configuration.credentialsConfiguration))
        builder.sslSocketFactory(HttpUtil.sslSocketFactory(), HttpUtil.trustAllCerts[0] as X509TrustManager)
        return builder.build()
    }
}
