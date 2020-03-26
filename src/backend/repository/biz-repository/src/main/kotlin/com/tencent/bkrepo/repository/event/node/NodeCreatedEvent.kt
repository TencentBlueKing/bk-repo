package com.tencent.bkrepo.repository.event.node

import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

data class NodeCreatedEvent(
    val repositoryInfo: RepositoryInfo,
    val nodeInfo: NodeInfo
)