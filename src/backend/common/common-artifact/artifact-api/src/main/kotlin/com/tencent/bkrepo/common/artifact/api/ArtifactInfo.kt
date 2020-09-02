package com.tencent.bkrepo.common.artifact.api

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 构件信息
 * [projectId]代表项目名，[repoName]代表仓库名，[artifactUri]代表构件完整uri, [artifact]代表构件名称，[version]为构件版本
 */
abstract class ArtifactInfo(
    /**
     * 项目名称
     */
    open val projectId: String,
    /**
     * 仓库名称
     */
    open val repoName: String,
    /**
     * 构件完整uri，如/archive/file/tmp.data
     */
    open val artifactUri: String,
    /**
     * 构件名称，如tmp.data
     */
    open val artifact: String = artifactUri,
    /**
     * 构件版本
     */
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

    fun getRepoIdentify(): String {
        val builder = StringBuilder()
        builder.append(projectId)
            .append(StringPool.SLASH)
            .append(repoName)
        return builder.toString()
    }
}
