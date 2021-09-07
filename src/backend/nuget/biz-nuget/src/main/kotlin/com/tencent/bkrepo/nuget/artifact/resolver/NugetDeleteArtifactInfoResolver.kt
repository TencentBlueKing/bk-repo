package com.tencent.bkrepo.nuget.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.nuget.constant.ID
import com.tencent.bkrepo.nuget.constant.VERSION
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDeleteArtifactInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(NugetDeleteArtifactInfo::class)
class NugetDeleteArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        // 判断是客户端的请求还是页面发送的请求分别进行处理
        val requestURL = request.requestURL
        // 页面删除包请求
        return if (requestURL.contains("/ext/package/delete/$projectId/$repoName")) {
            val packageKey = request.queryString.substringAfterLast("=")
            NugetDeleteArtifactInfo(projectId, repoName, packageKey)
        } else if (requestURL.contains("/ext/version/delete/$projectId/$repoName")) {
            val parameters = request.queryString.split("&")
            val packageKey = parameters.first().substringAfterLast("=")
            val version = parameters.last().substringAfterLast("=")
            NugetDeleteArtifactInfo(projectId, repoName, packageKey, version)
        } else {
            val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
            val id = attributes[ID].toString().trim()
            val version = attributes[VERSION].toString().trim()
            NugetDeleteArtifactInfo(projectId, repoName, PackageKeys.ofNuget(id), version)
        }
    }
}
