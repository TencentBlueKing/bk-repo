package com.tencent.bkrepo.fs.server.listener

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.fs.server.service.FileNodeService
import com.tencent.bkrepo.fs.server.service.node.RNodeService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class NodeModifyListener(
    private val fileNodeService: FileNodeService,
    private val nodeService: RNodeService
) {

    fun accept(message: Message<ArtifactEvent>) {
        val event = message.payload
        val type = event.type
        // 覆盖创建也会先删除，再创建。所以这里只需关注删除事件即可。
        if (type == EventType.NODE_DELETED) {
            logger.info("accept artifact delete event: $event, header: ${message.headers}")
            taskExecutor.execute { consumer(event) }
        }
    }

    private fun consumer(event: ArtifactEvent) {
        runBlocking {
            with(event) {
                val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, resourceKey))
                if (node?.folder != true) {
                    fileNodeService.deleteNodeBlocks(projectId, repoName, resourceKey)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyListener::class.java)
        private val taskExecutor = Executors.newSingleThreadExecutor()
    }
}
