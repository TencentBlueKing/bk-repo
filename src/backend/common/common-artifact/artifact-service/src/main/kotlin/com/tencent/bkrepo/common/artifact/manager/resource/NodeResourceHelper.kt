package com.tencent.bkrepo.common.artifact.manager.resource

import com.tencent.bkrepo.common.artifact.pojo.RepositoryId
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail

internal object NodeResourceHelper {
    fun getRepoDetail(node: NodeInfo): RepositoryDetail {
        with(node) {
            // 如果当前上下文存在该node的repo信息，则返回上下文中的repo，大部分请求应该命中这
            ArtifactContextHolder.getRepoDetail()?.let {
                if (it.projectId == projectId && it.name == repoName) {
                    return it
                }
            }
            // 如果是异步或者请求上下文找不到，则通过查询，并进行缓存
            val repositoryId = RepositoryId(
                projectId = projectId,
                repoName = repoName,
            )
            return ArtifactContextHolder.getRepoDetail(repositoryId)
        }
    }
}
