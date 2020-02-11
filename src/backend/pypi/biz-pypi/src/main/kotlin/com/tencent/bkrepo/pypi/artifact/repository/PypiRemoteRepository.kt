package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.UnsupportedOperationException
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiRemoteRepository : RemoteRepository() {
    override fun onUpload(context: ArtifactUploadContext) {
        throw UnsupportedOperationException()
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
        val listUri = generateRemoteListUrl(context)
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/html; charset=UTF-8"
        val okHttpClient = OkHttpClient()
        val build: Request = Request.Builder().get().url(listUri).build()
        val htmlContent: String? = okHttpClient.newCall(build).execute().body()?.string()
        response.writer.print(htmlContent)
    }
}
