package com.tencent.bkrepo.common.metadata.listener

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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
@Conditional(SyncCondition::class)
class BlockNodeDeletedEventListener(
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
            val artifactInfo = ArtifactInfo(projectId, repoName, resourceKey)
            val deletedNode = nodeService.getDeletedNodeDetail(artifactInfo).firstOrNull()
            if (deletedNode?.sha256 == FAKE_SHA256 && !deletedNode.folder) {
                // 获取当前存在的node（如果有新版本上传）
                val currentNode = nodeService.getNodeDetail(artifactInfo)
                
                if (currentNode != null) {
                    // 如果当前存在新node，只删除创建时间早于新node的blocks
                    // 因为updateBlockUploadId会更新blocknode.createdDate，所以：
                    // - 旧blocknode.createdDate < 新node.createdDate（旧版本的blocks）
                    // - 新blocknode.createdDate >= 新node.createdDate（新版本的blocks）
                    val createdBeforeOrAt = LocalDateTime.parse(
                        currentNode.createdDate,
                        DateTimeFormatter.ISO_DATE_TIME
                    )
                    // 只删除创建时间不晚于新node的blocks
                    // 因为updateBlockUploadId会在blockBaseNodeCreate之后执行
                    // 所以新blocknode.createdDate > 新node.createdDate，不会被误删
                    blockNodeService.deleteBlocks(
                        projectId,
                        repoName,
                        resourceKey,
                        uploadId = null,  // 只删除已完成的blocks（uploadId=null）
                        createdBeforeOrAt = createdBeforeOrAt
                    )
                    logger.info(
                        "Deleted old blocks for node[$projectId/$repoName$resourceKey] " +
                                "created before ${currentNode.createdDate}"
                    )
                } else {
                    // 如果当前不存在node，说明是真正的删除操作，删除所有已完成的blocks
                    blockNodeService.deleteBlocks(
                        projectId,
                        repoName,
                        resourceKey,
                        uploadId = null,  // 只删除已完成的blocks（uploadId=null）
                        createdBeforeOrAt = null  // 删除所有
                    )
                    logger.info("Deleted all blocks for deleted node[$projectId/$repoName$resourceKey]")
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlockNodeDeletedEventListener::class.java)
    }
}