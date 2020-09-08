package com.tencent.bkrepo.common.artifact.resolve.path

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import javax.servlet.http.HttpServletRequest

/**
 * 构件路径解析
 */
interface ArtifactInfoResolver {
    fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): ArtifactInfo
}
