package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import com.tencent.bkrepo.repository.pojo.node.NodeRequest

abstract class NodeEvent(
    open val nodeRequest: NodeRequest,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.NODE
    override fun getResourceKey() = "/${nodeRequest.projectId}/${nodeRequest.repoName}${nodeRequest.fullPath}"
}
