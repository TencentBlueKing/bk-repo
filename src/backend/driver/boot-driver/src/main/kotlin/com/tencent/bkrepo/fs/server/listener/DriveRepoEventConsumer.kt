package com.tencent.bkrepo.fs.server.listener

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.impl.RRepositoryServiceImpl
import com.tencent.bkrepo.fs.server.service.drive.DriveRepositoryService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Drive 仓库事件消费者
 *
 * 消费消息队列中的仓库创建/删除事件，当仓库类型为 DRIVE 时自动执行初始化/清理操作。
 */
@Component
class DriveRepoEventConsumer(
    private val driveRepositoryService: DriveRepositoryService,
    private val repositoryService: RRepositoryServiceImpl,
) {

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.REPO_CREATED,
    )

    fun accept(message: Message<ArtifactEvent>) {
        val event = message.payload
        if (!acceptTypes.contains(event.type)) {
            return
        }
        try {
            runBlocking {
                handleEvent(event)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to handle drive repo event[${event.type}] " +
                        "for [${event.projectId}/${event.repoName}]",
                e
            )
        }
    }

    private suspend fun handleEvent(event: ArtifactEvent) {
        val projectId = event.projectId
        val repoName = event.repoName

        val repo = repositoryService.getRepoDetailIncludeDeleted(projectId, repoName)
        // 判断仓库类型是否为 DRIVE
        if (repo?.type != RepositoryType.DRIVE) {
            return
        }

        when (event.type) {
            EventType.REPO_CREATED -> {
                logger.info("Received REPO_CREATED event for drive repository[$projectId/$repoName], initializing...")
                driveRepositoryService.initDriveRepository(repo, repo.createdBy)
            }

            else -> { /* 不处理的事件 */
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveRepoEventConsumer::class.java)
    }
}
