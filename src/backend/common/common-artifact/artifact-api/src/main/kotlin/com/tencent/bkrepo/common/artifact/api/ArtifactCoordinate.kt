package com.tencent.bkrepo.common.artifact.api

/**
 * 构件位置信息
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
data class ArtifactCoordinate(
    val projectId: String,
    val repoName: String,
    val artifactPath: ArtifactPath
)
