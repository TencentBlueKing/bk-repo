package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.docker.constant.BLOB_PATTERN
import com.tencent.bkrepo.docker.util.PathUtil
import java.util.LinkedList
import java.util.StringTokenizer
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(DockerArtifactInfo::class)
class DockerArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        val uri = request.requestURI
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        val pathElements = LinkedList<String>()
        val tokenizer = StringTokenizer(uri, "/")
        while (tokenizer.hasMoreTokens()) {
            pathElements.add(tokenizer.nextToken())
        }
        if (pathElements.size < 3) {
            logger.debug(
                "Cannot build MavenArtifactInfo from '{}'. The groupId, artifactId and version are unreadable.",
                uri
            )
            return DockerArtifactInfo("", "", "", "", "", "")
        }
        var pos = pathElements.size - 2

        val version = pathElements[pos--]
        val artifactId = pathElements[pos--]
        val groupIdBuff = StringBuilder()
        while (pos >= 2) {
            if (groupIdBuff.isNotEmpty()) {
                groupIdBuff.insert(0, '.')
            }
            groupIdBuff.insert(0, pathElements[pos])
            pos--
        }
        val groupId = groupIdBuff.toString()

        val mavenArtifactInfo = DockerArtifactInfo(projectId, repoName, artifactUri, groupId, artifactId, version)

        require(mavenArtifactInfo.isValid()) {
            throw IllegalArgumentException("Invalid unit info for '${mavenArtifactInfo.artifactUri}'.")
        }

        return mavenArtifactInfo
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactInfoResolver::class.java)
    }
}
