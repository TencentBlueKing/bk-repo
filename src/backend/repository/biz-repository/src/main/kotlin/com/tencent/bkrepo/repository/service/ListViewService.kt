package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 * 列表视图服务
 */
interface ListViewService {
    fun listNodeView(artifactInfo: ArtifactInfo)
    fun listRepoView(projectId: String)
    fun listProjectView()
}
