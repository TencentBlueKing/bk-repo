package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SurplusNodeCleaner {
    @Autowired
    lateinit var nodeResource: NodeResource

    /**
     * 删除目标List<NodeInfo> 中排序 >=2 的节点
     */
    fun deleteSurplusNode(list: List<NodeInfo>) {
        if (list.size > 2) {
            val surplusNodes = list.subList(2, list.size)
            for (node in surplusNodes) {
                nodeResource.delete(NodeDeleteRequest(node.projectId, node.repoName, node.fullPath, node.createdBy))
            }
        }
    }
}
