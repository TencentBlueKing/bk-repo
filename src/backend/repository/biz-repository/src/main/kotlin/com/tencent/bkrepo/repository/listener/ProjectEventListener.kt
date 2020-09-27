package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.stream.message.project.ProjectCreatedMessage
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.listener.event.project.ProjectCreatedEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ProjectEventListener @Autowired constructor(
    private val permissionManager: PermissionManager
) : AbstractEventListener() {

    @Async
    @EventListener(ProjectCreatedEvent::class)
    fun handle(event: ProjectCreatedEvent) {
        event.apply { sendMessage(ProjectCreatedMessage(request)) }
            .also { logEvent(it) }
            .takeIf { event.request.operator != SYSTEM_USER }?.run {
                permissionManager.registerProject(event.request.operator, event.request.name)
            }
    }
}
