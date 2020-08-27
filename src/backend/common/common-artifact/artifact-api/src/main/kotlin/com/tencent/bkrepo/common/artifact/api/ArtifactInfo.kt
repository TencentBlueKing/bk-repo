package com.tencent.bkrepo.common.artifact.api

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 构件信息
 */
abstract class ArtifactInfo(
    open val projectId: String,
    open val repoName: String,
    open val artifactUri: String,
    open val artifact: String = artifactUri,
    open val version: String? = null
) {
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append(projectId)
            .append(StringPool.SLASH)
            .append(repoName)
            .append(artifact)
        version?.let { builder.append(StringPool.DASH).append(it) }
        return builder.toString()
    }
}
