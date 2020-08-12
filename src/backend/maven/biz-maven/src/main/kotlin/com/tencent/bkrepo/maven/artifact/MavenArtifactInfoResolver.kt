package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest

@Resolver(MavenArtifactInfo::class)
class MavenArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): MavenArtifactInfo {
        val mavenArtifactInfo = MavenArtifactInfo(projectId, repoName, artifactUri)
        // 仅当上传jar包时处理metadata
        if (artifactUri.endsWith(".jar")) {
            val paths = artifactUri.split("/")
            if (paths.size < pathMinLimit) {
                logger.debug(
                    "Cannot build MavenArtifactInfo from '{}'. The groupId, artifactId and version are unreadable.",
                    artifactUri
                )
                return MavenArtifactInfo("", "", "")
            }
            var pos = paths.size - groupMark

            mavenArtifactInfo.versionId = paths[pos--]
            mavenArtifactInfo.artifactId = paths[pos--]
            val stringBuilder = StringBuilder()
            while (pos >= 0) {
                if (stringBuilder.isNotEmpty()) {
                    stringBuilder.insert(0, '.')
                }
                stringBuilder.insert(0, paths[pos--])
            }
            mavenArtifactInfo.groupId = stringBuilder.toString()

            require(mavenArtifactInfo.isValid()) {
                throw IllegalArgumentException("Invalid unit info for '${mavenArtifactInfo.artifactUri}'.")
            }
        }
        return mavenArtifactInfo
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MavenArtifactInfoResolver::class.java)
        // artifact uri 最少请求参数 group/artifact/[version]/filename
        private const val pathMinLimit = 3
        private const val groupMark = 2
    }
}
