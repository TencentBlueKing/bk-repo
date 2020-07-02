package com.tencent.bkrepo.repository.listener.event.metadata

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import com.tencent.bkrepo.repository.pojo.node.NodeRequest

abstract class MetadataEvent(
    open val nodeRequest: NodeRequest,
    open val operator: String
) : IEvent(operator) {
    override fun getResourceType() = ResourceType.METADATA
    override fun getResourceKey() = "/${nodeRequest.projectId}/${nodeRequest.repoName}${nodeRequest.fullPath}"
    override fun getRequest() = mapOf("request" to nodeRequest)
}
