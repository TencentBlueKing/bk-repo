package com.tencent.bkrepo.common.artifact.api

/**
 * 构件坐标
 * @author: carrypan
 * @date: 2019/11/21
 */
interface ArtifactCoordinate {
    val fullPath: String
}

/**
 * 默认实现，只保留fullPath
 */
data class DefaultArtifactCoordinate(override val fullPath: String): ArtifactCoordinate