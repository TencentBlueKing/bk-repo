package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.repository.listener.event.node.NodeCopiedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeCreatedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeDeletedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeMovedEvent
import com.tencent.bkrepo.repository.listener.event.node.NodeRenamedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class NodeEventListener : AbstractEventListener() {

    @Async
    @EventListener(NodeCreatedEvent::class)
    fun handle(event: NodeCreatedEvent) {
        event.apply {
            if (!event.node.folder) {
                NodeCreatedMessage(
                    projectId = node.projectId,
                    repoName = node.repoName,
                    fullPath = node.fullPath,
                    size = node.size,
                    sha256 = node.sha256!!,
                    md5 = node.md5!!,
                    operator = operator
                ).apply { sendMessage(this) }
            }
        }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeRenamedEvent::class)
    fun handle(event: NodeRenamedEvent) {
        event.apply {
            NodeRenamedMessage(
                projectId = node.projectId,
                repoName = node.repoName,
                fullPath = node.fullPath,
                newFullPath = newFullPath,
                operator = operator
            ).apply { sendMessage(this) }
        }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeMovedEvent::class)
    fun handle(event: NodeMovedEvent) {
        event.apply {
            NodeMovedMessage(
                srcProjectId = node.projectId,
                srcRepoName = node.repoName,
                srcFullPath = node.fullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = operator
            ).apply { sendMessage(this) }
        }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeCopiedEvent::class)
    fun handle(event: NodeCopiedEvent) {
        event.apply {
            NodeCopiedMessage(
                srcProjectId = node.projectId,
                srcRepoName = node.repoName,
                srcFullPath = node.fullPath,
                destProjectId = destProjectId,
                destRepoName = destRepoName,
                destFullPath = destFullPath,
                overwrite = overwrite,
                operator = operator
            ).apply { sendMessage(this) }
        }.also { logEvent(it) }
    }

    @Async
    @EventListener(NodeDeletedEvent::class)
    fun handle(event: NodeDeletedEvent) {
        event.apply {
            NodeDeletedMessage(
                projectId = node.projectId,
                repoName = node.repoName,
                fullPath = node.fullPath,
                operator = operator
            ).apply { sendMessage(this) }
        }.also { logEvent(it) }
    }
}
