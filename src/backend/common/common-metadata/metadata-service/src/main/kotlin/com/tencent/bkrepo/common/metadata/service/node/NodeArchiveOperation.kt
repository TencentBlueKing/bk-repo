package com.tencent.bkrepo.common.metadata.service.node

import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeArchiveRequest

/**
 * 节点归档操作
 * */
interface NodeArchiveOperation {
    /**
     * 归档节点
     * */
    fun archiveNode(nodeArchiveRequest: NodeArchiveRequest)

    /**
     * 恢复节点
     * */
    fun restoreNode(nodeArchiveRequest: NodeArchiveRequest)
}
