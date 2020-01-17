package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.util.LinkedList
import java.util.StringTokenizer
import javax.servlet.http.HttpServletRequest

@Resolver(NpmArtifactInfo::class)
class NpmArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): NpmArtifactInfo {
        // 校验npm版本号的正则表达式
        val versionPattern = "\\d.\\d.\\d"
        val uri = URLDecoder.decode(request.requestURI, "utf-8")

        val pathElements = LinkedList<String>()
        val stringTokenizer = StringTokenizer(uri, "/")
        while (stringTokenizer.hasMoreTokens()) {
            pathElements.add(stringTokenizer.nextToken())
        }
        if (pathElements.size < 3) {
            logger.debug(
                "Cannot build NpmArtifactInfo from '{}'. The pkgName are unreadable.",
                uri
            )
            return NpmArtifactInfo("", "", "")
        }
        val scope = if (pathElements[2].contains('@')) pathElements[2] else ""
        val pkgName = if (scope.contains('@')) pathElements[3] else pathElements[2]
        var version =
            if (pathElements.size > 4) pathElements[4] else (if (StringUtils.isBlank(scope) && pathElements.size == 4) pathElements[3] else "")
        version = versionPattern.toRegex().matchEntire(version)?.value ?: ""
        val npmArtifactInfo = NpmArtifactInfo(projectId, repoName, artifactUri, scope, pkgName, version)
        require(npmArtifactInfo.isValid()) {
            throw IllegalArgumentException("Invalid value for '${npmArtifactInfo.pkgName}'.")
        }
        return npmArtifactInfo
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmArtifactInfo::class.java)
    }
}
