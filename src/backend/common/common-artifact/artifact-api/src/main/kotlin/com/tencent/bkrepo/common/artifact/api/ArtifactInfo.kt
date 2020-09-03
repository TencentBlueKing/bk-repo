package com.tencent.bkrepo.common.artifact.api

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils

/**
 * 构件信息
 *
 * [projectId]为项目名，[repoName]为仓库名，[artifactUri]为构件完整uri
 */
abstract class ArtifactInfo(
    /**
     * 项目名称
     */
    val projectId: String,
    /**
     * 仓库名称
     */
    val repoName: String,
    /**
     * 构件完整uri，如/archive/file/tmp.data
     */
    private val artifactUri: String? = null
) {
    /**
     * 构件名称，不同依赖源解析规则不一样，可以override
     *
     * 默认使用传入的artifactUri作为名称
     */
    open fun getArtifactName(): String = artifactUri.orEmpty()

    /**
     * 构件版本
     *
     */
    open fun getArtifactVersion(): String? = null

    /**
     * 构件对应的节点完整路径，不同依赖源解析规则不一样，可以override
     *
     * 默认使用传入的artifactUri作为名称
     */
    open fun getArtifactFullPath(): String = artifactUri.orEmpty()

    /**
     * 构件下载显示名称，不同依赖源解析规则不一样，可以override
     *
     * 默认使用传入的artifactUri作为名称
     */
    open fun getResponseName(): String = PathUtils.resolveName(artifactUri.orEmpty())

    /**
     * 获取仓库唯一名, 格式 /{projectId}/{repoName}
     */
    open fun getRepoIdentify(): String {
        val builder = StringBuilder()
        builder.append(projectId)
            .append(StringPool.SLASH)
            .append(repoName)
        return builder.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append(getRepoIdentify())
            .append(StringPool.SLASH)
            .append(getArtifactName())
        getArtifactVersion()?.let { builder.append(StringPool.DASH).append(it) }
        return builder.toString()
    }
}
