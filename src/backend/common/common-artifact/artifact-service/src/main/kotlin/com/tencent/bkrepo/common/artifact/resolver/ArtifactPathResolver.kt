package com.tencent.bkrepo.common.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactPath
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactPath

/**
 * 构件路径解析
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
interface ArtifactPathResolver {
    fun resolve(fullPath: String): ArtifactPath
}

class DefaultArtifactPathResolver: ArtifactPathResolver {
    override fun resolve(fullPath: String): ArtifactPath {
        return DefaultArtifactPath(fullPath)
    }
}