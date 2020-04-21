package com.tencent.bkrepo.composer.artifact.url

import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.util.DecompressUtil
import com.tencent.bkrepo.composer.util.JsonUtil.jsonValue
import com.tencent.bkrepo.composer.util.UriUtil
import javax.servlet.http.HttpServletRequest

object UrlPatternUtil {
    fun fileUpload(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest
    ): ComposerArtifactInfo{
        //get uploadFile : name, version
        //todo 忽略用户设定的参数，直接从流中获取信息，如何获取上传文件的压缩类型。解压后才能获取json的信息。
        val artifactUri = request.requestURI.split("/")[3]
        val inputStream = request.inputStream
        UriUtil.getUriArgs(artifactUri.removePrefix("/").removeSuffix("/"))?.let { args ->
            args["format"]?.let { DecompressUtil.getComposerJson(inputStream, it) }
        }?.let { json ->
            return ComposerArtifactInfo(projectId, repoName, artifactUri,
                    json jsonValue "name",
                    json jsonValue "version",
            null)
        }
        //TODO interrupt
        return ComposerArtifactInfo(projectId, repoName, artifactUri,
                null,
                null,
                null)
    }
}