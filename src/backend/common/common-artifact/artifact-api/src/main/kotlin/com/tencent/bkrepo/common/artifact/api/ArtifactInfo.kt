package com.tencent.bkrepo.common.artifact.api

/**
 * 构件信息
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
abstract class ArtifactInfo(
    open val projectId: String,
    open val repoName: String,
    open val artifactUri: String
) {
    fun getFullUri() = "$projectId/$repoName$artifactUri"
    override fun toString() = getFullUri()
}
