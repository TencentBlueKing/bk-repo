package com.tencent.bkrepo.composer.artifact.repository

import com.google.gson.JsonParser
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.composer.INIT_PACKAGES
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperPackageJson
import okhttp3.Request
import org.springframework.stereotype.Component

@Component
class ComposerRemoteRepository: RemoteRepository(), ComposerRepository{
    override fun packages(context: ArtifactSearchContext): String? {
        val artifactInfo = context.artifactInfo
        val request = HttpContextHolder.getRequest()
        val host = "http://${request.remoteHost}:${request.serverPort}/${artifactInfo.projectId}/${artifactInfo.repoName}"
        return INIT_PACKAGES.wrapperPackageJson(host)
    }

    override fun getJson(context: ArtifactSearchContext): String? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        val remotePackagesUri = "${remoteConfiguration.url.removeSuffix("/")}$artifactUri"
        val okHttpClient = createHttpClient(remoteConfiguration)
        val request = Request.Builder().url(remotePackagesUri)
                .addHeader("Connection", "keep-alive")
                .get().build()
        val result = okHttpClient.newCall(request).execute().body()?.string()
        try {
            JsonParser().parse(result).asJsonObject
        } catch (e: Exception) {
            return null
        }
        return result
    }

}