package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeUpdatedMessage
import com.tencent.bkrepo.replication.exception.WaitPreorderNodeFailedException
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

/**
 * handler node message and replicate
 * include create ,copy ,rename,move
 * @author: owenlxu
 * @date: 2020/05/20
 */
@Component
class NodeEventHandler : AbstractEventHandler() {

    @Async
    @EventListener(NodeCreatedMessage::class)
    fun handle(message: NodeCreatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    try {
                        val remoteProjectId = getRemoteProjectId(it, projectId)
                        val remoteRepoName = getRemoteRepoName(it, repoName)
                        var context = ReplicationContext(it)
                        logger.info("start to handle create event [$projectId,$repoName,$fullPath]")
                        this.copy(
                            projectId = remoteProjectId,
                            repoName = remoteRepoName
                        ).apply { replicationService.replicaNodeCreateRequest(context, this) }
                        return@forEach
                    } catch (ignored: Exception) {
                        logger.warn("create node miss [$projectId,$repoName,$fullPath,${ignored.message}]")
                        retryCount -= 1
                        if (retryCount == 0) {
                            logger.error("create node failed [$projectId,$repoName,$fullPath,${ignored.message}]")
                            // log to db
                        }
                    }
                }
            }
        }
    }

    @Async
    @EventListener(NodeRenamedMessage::class)
    fun handle(message: NodeRenamedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    val remoteProjectId = getRemoteProjectId(it, projectId)
                    val remoteRepoName = getRemoteRepoName(it, repoName)
                    val context = ReplicationContext(it)
                    try {
                        logger.info("start to handle rename event [$projectId,$repoName,$fullPath]")
                        val result = waitForPreorderNode(context, remoteProjectId, remoteRepoName, fullPath)
                        if (!result) throw WaitPreorderNodeFailedException("rename time out")
                        this.copy(
                            projectId = remoteProjectId,
                            repoName = remoteRepoName
                        ).apply { replicationService.replicaNodeRenameRequest(context, this) }
                        return@forEach
                    } catch (ignored: Exception) {
                        logger.warn("rename node miss [$projectId,$repoName,$fullPath,${ignored.message}]")
                        retryCount -= 1
                        if (retryCount == 0) {
                            logger.error("rename node failed [$projectId,$repoName,$fullPath,${ignored.message}]")
                            // log to db
                        }
                        return
                    }
                }
            }
        }
    }

    @Async
    @EventListener(NodeUpdatedMessage::class)
    fun handle(message: NodeUpdatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    val remoteProjectId = getRemoteProjectId(it, projectId)
                    val remoteRepoName = getRemoteRepoName(it, repoName)
                    val context = ReplicationContext(it)
                    try {
                        logger.info("start to handle update event [$projectId,$repoName,$fullPath]")
                        val result = waitForPreorderNode(context, remoteProjectId, remoteRepoName, fullPath)
                        if (!result) throw WaitPreorderNodeFailedException("update time out")

                        this.copy(
                            projectId = remoteProjectId,
                            repoName = remoteRepoName
                        ).apply { replicationService.replicaNodeUpdateRequest(context, this) }
                        return@forEach
                    } catch (ignored: Exception) {
                        logger.warn("update node miss [$projectId,$repoName,$fullPath,${ignored.message}]")
                        retryCount -= 1
                        if (retryCount == 0) {
                            logger.error("update node failed [$projectId,$repoName,$fullPath,${ignored.message}]")
                            // log to db
                        }
                        return
                    }
                }
            }
        }
    }

    @Async
    @EventListener(NodeCopiedMessage::class)
    fun handle(message: NodeCopiedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    val remoteProjectId = getRemoteProjectId(it, projectId)
                    val remoteRepoName = getRemoteRepoName(it, repoName)
                    val context = ReplicationContext(it)
                    try {
                        logger.info("start to handle copy event [$projectId,$repoName,$srcFullPath]")
                        val result = waitForPreorderNode(context, remoteProjectId, remoteRepoName, srcFullPath)
                        if (!result) throw WaitPreorderNodeFailedException("copy time out")
                        this.copy(
                            srcProjectId = remoteProjectId,
                            srcRepoName = remoteRepoName
                        ).apply { replicationService.replicaNodeCopyRequest(context, this) }
                        return@forEach
                    } catch (ignored: Exception) {
                        logger.warn("copy node miss [$projectId,$repoName,$srcFullPath,${ignored.message}]")
                        retryCount -= 1
                        if (retryCount == 0) {
                            logger.error("copy node failed [$projectId,$repoName,$srcFullPath,${ignored.message}]")
                            // log to db
                        }
                    }
                }
            }
        }
    }

    @Async
    @EventListener(NodeMovedMessage::class)
    fun handle(message: NodeMovedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    val remoteProjectId = getRemoteProjectId(it, projectId)
                    val remoteRepoName = getRemoteRepoName(it, repoName)
                    val context = ReplicationContext(it)
                    try{
                        logger.info("start to handle move event [$projectId,$repoName,$fullPath]")
                        val result = waitForPreorderNode(context, remoteProjectId, remoteRepoName, fullPath)
                        if (!result) throw WaitPreorderNodeFailedException("move time out")
                        this.copy(
                            srcProjectId = remoteProjectId,
                            srcRepoName = remoteRepoName
                        ).apply { replicationService.replicaNodeMoveRequest(context, this) }
                        return@forEach
                    }catch (ignored: Exception) {
                        retryCount -= 1
                        logger.warn("move node miss [$projectId,$repoName,$fullPath,${ignored.message}]")
                        if (retryCount == 0) {
                            logger.error("move node failed [$projectId,$repoName,$fullPath,${ignored.message}]")
                            // log to db
                        }
                    }

                }
            }

        }
    }

    @Async
    @EventListener(NodeDeletedMessage::class)
    fun handle(message: NodeDeletedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                var retryCount = EXCEPTION_RETRY_COUNT
                while (retryCount > 0) {
                    val remoteProjectId = getRemoteProjectId(it, projectId)
                    val remoteRepoName = getRemoteRepoName(it, repoName)
                    val context = ReplicationContext(it)
                    try {
                        logger.info("start to handle delete event [$projectId,$repoName,$fullPath]")
                        val result = waitForPreorderNode(context, remoteProjectId, remoteRepoName, fullPath)
                        if (!result) throw WaitPreorderNodeFailedException("delete time out")
                        this.copy(
                            projectId = remoteProjectId,
                            repoName = remoteRepoName
                        ).apply { replicationService.replicaNodeDeleteRequest(context, this) }
                        return@forEach
                    } catch (ignored: Exception) {
                        logger.warn("delete node miss [$projectId,$repoName,$fullPath,${ignored.message}]")
                        retryCount -= 1
                        if (retryCount == 0) {
                            logger.error("delete node failed [$projectId,$repoName,$fullPath,${ignored.message}]")
                            // log to db
                        }
                    }
                }
            }
        }
    }

    // wait max 120s for pre order node
    private fun waitForPreorderNode(
        context: ReplicationContext,
        projectId: String,
        repoName: String,
        fullPath: String
    ): Boolean {
        var retryCount = WAIT_RETRY_COUNT
        while (retryCount > 0) {
            val result = replicationService.checkNodeExistRequest(context, projectId, repoName, fullPath)
            if (result) return true
            retryCount -= 1
            sleep(1000)
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeEventHandler::class.java)
        private const val EXCEPTION_RETRY_COUNT = 3
        private const val WAIT_RETRY_COUNT = 120
    }
}
