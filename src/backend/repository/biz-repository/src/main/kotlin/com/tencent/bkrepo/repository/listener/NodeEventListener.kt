package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.event.node.NodeEvent
import com.tencent.bkrepo.common.stream.producer.StreamProducer
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NodeEventListener(
    private val streamProducer: StreamProducer
) {

    @EventListener(NodeEvent::class)
    fun listen(event: NodeEvent) {
        // 审计日志
        // mq
        streamProducer.sendEvent(event)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeEventListener::class.java)
    }
}
