package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.stereotype.Component

@Component
class SurplusNodeCleaner(
    private val nodeClient: NodeClient
) {
    /**
     * 删除目标List<NodeInfo> 中排序 >=2 的节点
     */
    fun deleteSurplusNode(list: List<NodeInfo>) {
        if (list.size > 2) {
            val surplusNodes = list.subList(2, list.size)
            for (node in surplusNodes) {
                nodeClient.delete(NodeDeleteRequest(node.projectId, node.repoName, node.fullPath, node.createdBy))
            }
        }
    }
}
