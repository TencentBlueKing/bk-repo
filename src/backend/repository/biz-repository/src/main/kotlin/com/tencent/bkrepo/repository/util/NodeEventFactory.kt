package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.artifact.event.metadata.MetadataDeletedEvent
import com.tencent.bkrepo.common.artifact.event.metadata.MetadataSavedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeCopiedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeCreatedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeDeletedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.event.node.NodeRenamedEvent
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest

/**
 * 节点事件构造类
 */
object NodeEventFactory {

    /**
     * 节点创建事件
     */
    fun buildCreatedEvent(node: TNode): NodeCreatedEvent {
        with(node) {
            return NodeCreatedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = node.createdBy
            )
        }
    }

    /**
     * 节点删除事件
     */
    fun buildDeletedEvent(
        projectId: String,
        repoName: String,
        fullPath: String,
        userId: String
    ): NodeDeletedEvent {
        return NodeDeletedEvent(
            projectId = projectId,
            repoName = repoName,
            resourceKey = fullPath,
            userId = userId
        )
    }

    /**
     * 节点重命名事件
     */
    fun buildRenamedEvent(request: NodeRenameRequest): NodeRenamedEvent {
        with(request) {
            return NodeRenamedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = operator,
                newFullPath = newFullPath
            )
        }
    }

    /**
     * 节点移动事件
     */
    fun buildMovedEvent(request: NodeMoveCopyRequest): NodeMovedEvent {
        with(request) {
            return NodeMovedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = operator,
                dstProjectId = destProjectId ?: projectId,
                dstRepoName = destRepoName ?: repoName,
                dstFullPath = destFullPath
            )
        }
    }

    /**
     * 节点拷贝事件
     */
    fun buildCopiedEvent(request: NodeMoveCopyRequest): NodeCopiedEvent {
        with(request) {
            return NodeCopiedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = operator,
                dstProjectId = destProjectId ?: projectId,
                dstRepoName = destRepoName ?: repoName,
                dstFullPath = destFullPath
            )
        }
    }

    /**
     * 元数据保存事件
     */
    fun buildMetadataSavedEvent(request: MetadataSaveRequest): MetadataSavedEvent {
        with(request) {
            return MetadataSavedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = operator,
                metadata = metadata.orEmpty()
            )
        }
    }

    /**
     * 元数据删除事件
     */
    fun buildMetadataDeletedEvent(request: MetadataDeleteRequest): MetadataDeletedEvent {
        with(request) {
            return MetadataDeletedEvent(
                projectId = projectId,
                repoName = repoName,
                resourceKey = fullPath,
                userId = operator,
                keys = keyList
            )
        }
    }
}
