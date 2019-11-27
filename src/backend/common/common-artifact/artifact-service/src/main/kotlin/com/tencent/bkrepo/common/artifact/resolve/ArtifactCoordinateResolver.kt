package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactCoordinate

/**
 * 构件路径解析
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
interface ArtifactCoordinateResolver {
    fun resolve(fullPath: String): ArtifactCoordinate
}

class DefaultArtifactCoordinateResolver: ArtifactCoordinateResolver {
    override fun resolve(fullPath: String): ArtifactCoordinate {
        return DefaultArtifactCoordinate(fullPath)
    }
}