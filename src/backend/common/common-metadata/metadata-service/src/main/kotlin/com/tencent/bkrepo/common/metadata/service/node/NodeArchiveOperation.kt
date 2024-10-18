package com.tencent.bkrepo.common.metadata.service.node

import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest

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

    /**
     * 恢复归档节点
     * */
    fun restoreNode(nodeRestoreRequest: NodeArchiveRestoreRequest): List<String>

    /**
     * 获取可归档的节点大小
     */
    fun getArchivableSize(projectId: String, repoName: String?, days: Int, size: Long? = null): Long
}
