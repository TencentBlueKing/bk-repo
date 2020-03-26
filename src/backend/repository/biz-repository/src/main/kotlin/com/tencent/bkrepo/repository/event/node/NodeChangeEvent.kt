package com.tencent.bkrepo.repository.event.node

import com.sun.org.apache.xalan.internal.lib.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

abstract class NodeChangeEvent (
    open val repositoryInfo: RepositoryInfo,
    open val nodeInfo: NodeInfo
)