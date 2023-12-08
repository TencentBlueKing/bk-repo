package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.service.node.NodeArchiveOperation
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Update

class NodeArchiveSupport(
    private val nodeBaseService: NodeBaseService,
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

    companion object {
        private val logger = LoggerFactory.getLogger(NodeArchiveSupport::class.java)
    }
}
