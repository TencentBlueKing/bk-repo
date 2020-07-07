package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.npm.constants.FILE_SEPARATOR
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
        val uri = URLDecoder.decode(request.requestURI, characterEncoding)

        val pathElements = LinkedList<String>()
        val stringTokenizer = StringTokenizer(uri.substringBefore(delimiter), FILE_SEPARATOR)
        while (stringTokenizer.hasMoreTokens()) {
            pathElements.add(stringTokenizer.nextToken())
        }
        if (pathElements.size < 2) {
            logger.debug(
                "Cannot build NpmArtifactInfo from '{}'. The pkgName are unreadable.",
                uri
            )
            return NpmArtifactInfo("", "", "")
        }
        val scope = if (pathElements[2].contains(AT)) pathElements[2] else StringPool.EMPTY
        val pkgName = if (scope.contains(AT)) pathElements[3] else pathElements[2]
        val version =
            if (pathElements.size > 4) pathElements[4] else (if (StringUtils.isBlank(scope) && pathElements.size == 4) pathElements[3] else StringPool.EMPTY)
        return NpmArtifactInfo(projectId, repoName, artifactUri, scope, pkgName, version)
    }

    companion object {
        const val characterEncoding: String = "utf-8"
        const val delimiter: String = "/-rev"
        const val AT: Char = '@'
        val logger: Logger = LoggerFactory.getLogger(NpmArtifactInfo::class.java)
    }
}
