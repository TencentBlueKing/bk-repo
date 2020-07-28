package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.StringTokenizer
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(MavenArtifactInfo::class)
class MavenArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): MavenArtifactInfo {
        val uri = request.requestURI
        val pathElements = LinkedList<String>()
        val tokenizer = StringTokenizer(uri, "/")
        while (tokenizer.hasMoreTokens()) {
            pathElements.add(tokenizer.nextToken())
        }
        if (pathElements.size < pathMinLimit) {
            logger.debug("Cannot build MavenArtifactInfo from '{}'. The groupId, artifactId and version are unreadable.",
                    uri)
            return MavenArtifactInfo("", "", "", "", "", "")
        }
        var pos = pathElements.size - groupMark

        val version = pathElements[pos--]
        val artifactId = pathElements[pos--]
        val groupIdBuff = StringBuilder()
        while (pos >= groupMark) {
            if (groupIdBuff.isNotEmpty()) {
                groupIdBuff.insert(0, '.')
            }
            groupIdBuff.insert(0, pathElements[pos])
            pos--
        }
        val groupId = groupIdBuff.toString()

        val mavenArtifactInfo = MavenArtifactInfo(projectId, repoName, artifactUri, groupId, artifactId, version)

        require(mavenArtifactInfo.isValid()) {
            throw IllegalArgumentException("Invalid unit info for '${mavenArtifactInfo.artifactUri}'.")
        }

        return mavenArtifactInfo
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MavenArtifactInfoResolver::class.java)
        // artifact uri 最少请求参数
        private const val pathMinLimit = 3
        // maven构件请求格式：最后两个的参数固定是版本和包名，构造group(组织名)时去除最后两个字段
        private const val groupMark = 2
    }
}
