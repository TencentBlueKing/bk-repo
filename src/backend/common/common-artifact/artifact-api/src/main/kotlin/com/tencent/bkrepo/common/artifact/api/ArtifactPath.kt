package com.tencent.bkrepo.common.artifact.api

/**
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
interface ArtifactPath {
    val fullPath: String
}

data class DefaultArtifactPath(override val fullPath: String): ArtifactPath