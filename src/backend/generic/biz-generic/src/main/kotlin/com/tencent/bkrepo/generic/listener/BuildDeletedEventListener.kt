package com.tencent.bkrepo.generic.listener

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
@Conditional(SyncCondition::class)
class BuildDeletedEventListener(
    private val nodeService: NodeService,
    private val blockNodeService: BlockNodeService
) {

    @Async
    @EventListener(ArtifactEvent::class)
    fun handle(event: ArtifactEvent) {
        if (event.type == EventType.NODE_DELETED) {
            logger.info("accept artifact delete event: $event")
            consumer(event)
        }
    }

    private fun consumer(event: ArtifactEvent) {
        with(event) {
            val node = nodeService.getDeletedNodeDetail(ArtifactInfo(projectId, repoName, resourceKey)).firstOrNull()
            if (node?.sha256 == FAKE_SHA256 && !node.folder) {
                blockNodeService.deleteBlocks(projectId, repoName, resourceKey)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BuildDeletedEventListener::class.java)
    }
}