package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.project.ProjectCreatedMessage
import com.tencent.bkrepo.repository.listener.event.project.ProjectCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ProjectEventListener : AbstractEventListener() {

    @Async
    @EventListener(ProjectCreatedEvent::class)
    fun handle(event: ProjectCreatedEvent) {
        event.apply { sendMessage(ProjectCreatedMessage(request)) }.also { logEvent(it) }
    }

}
