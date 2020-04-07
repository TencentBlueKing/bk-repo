package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.metadata.MetadataDeletedMessage
import com.tencent.bkrepo.common.stream.message.metadata.MetadataSavedMessage
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataDeletedEvent
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataSavedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MetadataEventListener : AbstractEventListener() {

    @Async
    @EventListener(MetadataSavedEvent::class)
    fun handle(event: MetadataSavedEvent) {
        event.apply { sendMessage(MetadataSavedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(MetadataDeletedEvent::class)
    fun handle(event: MetadataDeletedEvent) {
        event.apply { sendMessage(MetadataDeletedMessage(request)) }.also { logEvent(it) }
    }

}
