package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.log.ResourceType

abstract class NodeEvent(
    open val node: TNode,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.NODE
    override fun getResourceKey() = "/${node.projectId}/${node.repoName}${node.fullPath}"
}
