package com.tencent.bkrepo.repository.event.node

import com.tencent.bkrepo.common.artifact.event.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.ResourceType
import com.tencent.bkrepo.repository.pojo.node.NodeRequest

/**
 * 节点抽象事件
 */
abstract class NodeEvent : ArtifactEvent {
    override val resourceType = ResourceType.NODE
    override val data: Map<String, Any> = mapOf()
}
