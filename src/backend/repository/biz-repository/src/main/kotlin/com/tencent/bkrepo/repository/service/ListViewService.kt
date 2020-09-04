package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 * 列表视图服务接口
 */
interface ListViewService {

    /**
     * 展示节点[artifactInfo]的子节点列表视图，通过`Http Servlet Response`直接输出视图内容
     *
     * 如果[artifactInfo]为目录则展示目录下的节点列表
     * 如果[artifactInfo]为文件则下载文件
     */
    fun listNodeView(artifactInfo: ArtifactInfo)

    /**
     * 展示项目[projectId]的仓库列表视图，通过`Http Servlet Response`直接输出视图内容
     */
    fun listRepoView(projectId: String)

    /**
     * 展示项目列表视图，通过`Http Servlet Response`直接输出视图内容
     */
    fun listProjectView()
}
