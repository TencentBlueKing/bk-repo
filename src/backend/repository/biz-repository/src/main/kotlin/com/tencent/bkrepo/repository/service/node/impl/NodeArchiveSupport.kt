package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.service.node.NodeArchiveOperation
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.Duration

class NodeArchiveSupport(
    private val nodeBaseService: NodeBaseService,
    private val archiveClient: ArchiveClient,
) : NodeArchiveOperation {
    val nodeDao: NodeDao = nodeBaseService.nodeDao

    override fun archiveNode(nodeArchiveRequest: NodeArchiveRequest) {
        with(nodeArchiveRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::archived.name, true)
            nodeDao.updateFirst(query, update)
            logger.info("Archive node $projectId/$repoName/$fullPath.")
        }
    }

    override fun restoreNode(nodeArchiveRequest: NodeArchiveRequest) {
        with(nodeArchiveRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::archived.name, false)
                .set(TNode::lastAccessDate.name, LocalDateTime.now())
            nodeDao.updateFirst(query, update)
            logger.info("Restore node $projectId/$repoName/$fullPath.")
        }
    }

    override fun restoreNode(nodeRestoreRequest: NodeArchiveRestoreRequest): List<String> {
        with(nodeRestoreRequest) {
            val query = NodeQueryHelper.queryArchiveNode(projectId, repoName, path, metadata)
            query.limit(limit)
            val nodes = nodeDao.find(query)
            logger.info("Find ${nodes.size} nodes to restore.")
            if (nodes.isEmpty()) {
                return emptyList()
            }
            val repoId = ArtifactContextHolder.RepositoryId(projectId, repoName)
            val repo = ArtifactContextHolder.getRepoDetail(repoId)
            val storageCredentialsKey = repo.storageCredentials?.key
            return nodes.map {
                val sha256 = it.sha256!!
                if (it.archived == true) {
                    val req = ArchiveFileRequest(sha256, storageCredentialsKey, operator)
                    archiveClient.restore(req)
                } else {
                    val req = UncompressFileRequest(sha256, storageCredentialsKey, operator)
                    archiveClient.uncompress(req)
                }
                logger.info("Restoring node $$projectId/$repoName/${it.fullPath}.")
                it.fullPath
            }
        }
    }

    override fun getArchivableSize(projectId: String, repoName: String?, days: Int, size: Long?): Long {
        val cutoffTime = LocalDateTime.now().minus(Duration.ofDays(days.toLong()))
        val criteria = where(TNode::folder).isEqualTo(false)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::sha256).ne(FAKE_SHA256)
            .and(TNode::archived).ne(true)
            .and(TNode::projectId).isEqualTo(projectId)
            .orOperator(
                where(TNode::lastAccessDate).isEqualTo(null),
                where(TNode::lastAccessDate).lt(cutoffTime),
            ).apply {
                repoName?.let { and(TNode::repoName).isEqualTo(it) }
                size?.let { and(TNode::size).gt(it) }
            }
        return nodeBaseService.aggregateComputeSize(criteria)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeArchiveSupport::class.java)
    }
}
