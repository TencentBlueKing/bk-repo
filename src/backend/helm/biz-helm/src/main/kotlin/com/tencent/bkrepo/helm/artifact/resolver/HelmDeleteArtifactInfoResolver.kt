package com.tencent.bkrepo.helm.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.pojo.artifact.HelmDeleteArtifactInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(HelmDeleteArtifactInfo::class)
class HelmDeleteArtifactInfoResolver : ArtifactInfoResolver {
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
            HelmDeleteArtifactInfo(projectId, repoName, packageKey)
        } else if (requestURL.contains("/ext/version/delete/$projectId/$repoName")) {
            val parameters = request.queryString.split("&")
            val packageKey = parameters.first().substringAfterLast("=")
            val version = parameters.last().substringAfterLast("=")
            HelmDeleteArtifactInfo(projectId, repoName, packageKey, version)
        } else {
            val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
            val id = attributes[NAME].toString().trim()
            val version = attributes[VERSION].toString().trim()
            HelmDeleteArtifactInfo(projectId, repoName, PackageKeys.ofNuget(id), version)
        }
    }
}
