package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.stream.event.node.NodeEvent
import com.tencent.bkrepo.common.stream.producer.StreamProducer
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NodeEventListener(
    private val streamProducer: StreamProducer
) {

    @EventListener(NodeEvent::class)
    fun listen(event: NodeEvent) {
        logEvent(event)
        sendEventStream(event)
    }

    fun logEvent(event: NodeEvent) {
        // TODO: 审计日志
    }

    fun sendEventStream(event: NodeEvent) {
        // TODO: 发送事件
        if (event.repoCategory == RepositoryCategory.LOCAL.name) {
            streamProducer.sendEvent(event)
        }
    }
}
