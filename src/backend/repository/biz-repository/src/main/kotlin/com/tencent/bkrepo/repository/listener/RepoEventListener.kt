package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.repo.RepoCreatedMessage
import com.tencent.bkrepo.common.stream.message.repo.RepoDeletedMessage
import com.tencent.bkrepo.common.stream.message.repo.RepoUpdatedMessage
import com.tencent.bkrepo.repository.listener.event.repo.RepoCreatedEvent
import com.tencent.bkrepo.repository.listener.event.repo.RepoDeletedEvent
import com.tencent.bkrepo.repository.listener.event.repo.RepoUpdatedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class RepoEventListener : AbstractEventListener() {

    @Async
    @EventListener(RepoCreatedEvent::class)
    fun handle(event: RepoCreatedEvent) {
        event.apply { sendMessage(RepoCreatedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(RepoUpdatedEvent::class)
    fun handle(event: RepoUpdatedEvent) {
        event.apply { sendMessage(RepoUpdatedMessage(request)) }.also { logEvent(it) }
    }

    @Async
    @EventListener(RepoDeletedEvent::class)
    fun handle(event: RepoDeletedEvent) {
        event.apply { sendMessage(RepoDeletedMessage(request)) }.also { logEvent(it) }
    }
}
