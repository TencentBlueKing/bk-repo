package com.tencent.bkrepo.replication.handler.event

import com.tencent.bkrepo.common.service.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.stream.message.node.NodeCopiedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeCreatedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeDeletedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeMovedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeRenamedMessage
import com.tencent.bkrepo.common.stream.message.node.NodeUpdatedMessage
import com.tencent.bkrepo.replication.exception.ReplicaFileFailedException
import com.tencent.bkrepo.replication.job.ReplicationContext
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
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

    @EventListener(NodeCreatedMessage::class)
    fun handle(message: NodeCreatedMessage) {
        with(message.request) {
            var retryCount = CTEATE_RETRY_COUNT
            while (retryCount > 0) {
                try {
                    getRelativeTaskList(projectId, repoName).forEach {
                        val remoteProjectId = getRemoteProjectId(it, projectId)
                        val remoteRepoName = getRemoteRepoName(it, repoName)
                        var context = ReplicationContext(it)

                        context.currentRepoDetail = getRepoDetail(projectId, repoName, remoteRepoName) ?: run {
                            logger.warn("found no repo detail [$projectId, $repoName]")
                            retryCount -= 3
                            return
                        }
                        logger.info("start to handle event [${message.request}]")
                        this.copy(
                            projectId = remoteProjectId,
                            repoName = remoteRepoName
                        ).apply { replicationService.replicaNodeCreateRequest(context, this) }
                    }
                    retryCount -= CTEATE_RETRY_COUNT
                    return
                } catch (exception: ReplicaFileFailedException) {
                    retryCount -= 1
                    if (retryCount == 0) {
                        logger.error("create file failed [${exception.message}]")
                        // log to db
                    }
                    return
                }
                catch (exception: ExternalErrorCodeException) {
                    retryCount -= 1
                    if (retryCount == 0) {
                        logger.error("create file failed [${exception.message}]")
                        // log to db
                    }
                    return
                }
            }
            // return
        }
    }

    @EventListener(NodeRenamedMessage::class)
    fun handle(message: NodeRenamedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeRenameRequest(context, this) }
            }
        }
    }

    @EventListener(NodeUpdatedMessage::class)
    fun handle(message: NodeUpdatedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeUpdateRequest(context, this) }
            }
        }
    }

    @EventListener(NodeCopiedMessage::class)
    fun handle(message: NodeCopiedMessage) {
        var retryCount = COPY_RETRY_COUNT
        while (retryCount > 0) {
            try {
                with(message.request) {
                    getRelativeTaskList(projectId, repoName).forEach {
                        val remoteProjectId = getRemoteProjectId(it, projectId)
                        val remoteRepoName = getRemoteRepoName(it, repoName)
                        val context = ReplicationContext(it)

                        context.currentRepoDetail = getRepoDetail(projectId, repoName, remoteRepoName) ?: run {
                            logger.warn("found no repo detail [$projectId, $repoName]")
                            retryCount -= COPY_RETRY_COUNT
                            return
                        }
                        logger.info("start to handle event [${message.request}]")
                        this.copy(
                            srcProjectId = remoteProjectId,
                            srcRepoName = remoteRepoName
                        ).apply { replicationService.replicaNodeCopyRequest(context, this) }
                    }
                }
            } catch (exception: ReplicaFileFailedException) {
                retryCount -= 1
                sleep(2000)
                if (retryCount == 0) {
                    logger.error("copy file failed [${exception.message}]")
                    // log to db
                }
                return
            } catch (exception: ExternalErrorCodeException) {
                retryCount -= 1
                sleep(2000)
                if (retryCount == 0) {
                    logger.error("copy file failed [${exception.message}]")
                    // log to db
                }
                return
            }
        }
    }

    @EventListener(NodeMovedMessage::class)
    fun handle(message: NodeMovedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                context.currentProjectDetail
                this.copy(
                    srcProjectId = getRemoteProjectId(it, projectId),
                    srcRepoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeMoveRequest(context, this) }
            }
        }
    }

    @EventListener(NodeDeletedMessage::class)
    fun handle(message: NodeDeletedMessage) {
        with(message.request) {
            getRelativeTaskList(projectId, repoName).forEach {
                val context = ReplicationContext(it)
                this.copy(
                    projectId = getRemoteProjectId(it, projectId),
                    repoName = getRemoteRepoName(it, repoName)
                ).apply { replicationService.replicaNodeDeleteRequest(context, this) }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeEventHandler::class.java)
        private const val CTEATE_RETRY_COUNT = 3
        private const val COPY_RETRY_COUNT = 5
    }
}
