package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeUpdatedMessage
import com.tencent.bkrepo.repository.listener.event.node.NodeCopiedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeCreatedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeDeletedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeMovedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeRenamedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeUpdatedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NodeEventListener : AbstractEventListener() {

    @Async
    @EventListener(NodeCreatedEvent::class)
    fun handle(event: NodeCreatedEvent) {
        event.apply { sendMessage(NodeCreatedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeRenamedEvent::class)
    fun handle(event: NodeRenamedEvent) {
        event.apply { sendMessage(NodeRenamedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeUpdatedEvent::class)
    fun handle(event: NodeUpdatedEvent) {
        event.apply { sendMessage(NodeUpdatedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeMovedEvent::class)
    fun handle(event: NodeMovedEvent) {
        event.apply { sendMessage(NodeMovedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeCopiedEvent::class)
    fun handle(event: NodeCopiedEvent) {
        event.apply { sendMessage(NodeCopiedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeDeletedEvent::class)
    fun handle(event: NodeDeletedEvent) {
        event.apply { sendMessage(NodeDeletedMessage(request)) }.also { logEvent(it) }
    }
}
