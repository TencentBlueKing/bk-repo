package com.tencent.bkrepo.repository.listener.event.node

import com.tencent.bkrepo.repository.listener.event.IEvent
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.log.ResourceType

abstract class NodeEvent(
    open val repository: TRepository,
    open val node: TNode
): IEvent {
    override fun getResourceType() = ResourceType.NODE
    override fun getResourceKey() = "/${node.projectId}/${node.repoName}${node.fullPath}"
    override fun getUserId() = node.lastModifiedBy
    override fun getDescription() = node.toString()
}