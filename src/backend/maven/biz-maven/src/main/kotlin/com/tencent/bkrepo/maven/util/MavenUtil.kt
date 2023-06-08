package com.tencent.bkrepo.maven.util

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.maven.enum.MavenMessageCode
import com.tencent.bkrepo.maven.exception.MavenBadRequestException
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object MavenUtil {
    private const val MAX_DIGEST_CHARS_NEEDED = 128

    /**
     * 从流中导出摘要
     * */
    fun extractDigest(inputStream: InputStream): String {
        inputStream.use {
            val reader = InputStreamReader(
                ByteStreams
                    .limit(inputStream, MAX_DIGEST_CHARS_NEEDED.toLong()),
                StandardCharsets.UTF_8
            )
            return CharStreams.toString(reader)
        }
    }

    /**
     * 提取出对应的artifactId和groupId
     */
    fun extractGroupIdAndArtifactId(packageKey: String): Pair<String, String> {
        val params = PackageKeys.resolveGav(packageKey)
        val artifactId = params.split(":").last()
        val groupId = params.split(":").first()
        return Pair(artifactId, groupId)
    }

    /**
     * 获取对应package存储的节点路径
     */
    fun extractPath(packageKey: String): String {
        val (artifactId, groupId) = extractGroupIdAndArtifactId(packageKey)
        return StringUtils.join(groupId.split("."), "/") + "/$artifactId"
    }

    /**
     * 从路径中提取出packageKey
     */
    fun extractPackageKey(fullPath: String): String {
        val pathList = fullPath.trim('/').split("/")
        if (pathList.size <= 1) throw MavenBadRequestException(MavenMessageCode.MAVEN_ARTIFACT_DELETE, fullPath)
        val artifactId = pathList.last()
        val groupId = StringUtils.join(pathList.subList(0, pathList.size - 1), ".")
        return PackageKeys.ofGav(groupId, artifactId)
    }
}
