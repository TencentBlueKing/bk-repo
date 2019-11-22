package com.tencent.bkrepo.common.artifact.api

/**
 * 构件信息
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
data class ArtifactInfo(
    val projectId: String,
    val repoName: String,
    val coordinate: ArtifactCoordinate
)
