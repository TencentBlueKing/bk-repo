package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.repository.listener.event.node.NodeCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NodeEventListener: AbstractEventListener() {

    @EventListener(NodeCreatedEvent::class)
    fun listen(event: NodeCreatedEvent) {
        logEvent(event)
        if (event.repository.category == RepositoryCategory.LOCAL && !event.node.folder) {
            val message = with(event.node) {
                 NodeCreatedMessage(
                     projectId = projectId,
                     repoName = repoName,
                     fullPath = fullPath,
                     size = size,
                     sha256 = sha256!!,
                     md5 = md5!!
                 )
            }
            sendMessage(message)
        }
    }

}
