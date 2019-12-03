package com.tencent.bkrepo.common.artifact.api

import javax.servlet.http.HttpServletRequest

/**
 * 构件路径解析
 *
 * @author: carrypan
 * @date: 2019/11/21
 */
interface ArtifactInfoResolver {
    fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): ArtifactInfo
}

