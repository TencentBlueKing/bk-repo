package com.tencent.bkrepo.common.artifact.locator

/**
 * 构件定位信息
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
data class ArtifactLocation(
    val projectId: String,
    val repoName: String,
    val fullPath: String
)
