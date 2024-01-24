package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUnCompressedRequest
import com.tencent.bkrepo.repository.service.node.NodeCompressOperation
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

class NodeCompressSupport(
    nodeBaseService: NodeBaseService,
) : NodeCompressOperation {
    val nodeDao: NodeDao = nodeBaseService.nodeDao
    override fun compressedNode(nodeCompressedRequest: NodeCompressedRequest) {
        with(nodeCompressedRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::compressed.name, true)
            nodeDao.updateFirst(query, update)
            logger.info("Success to compress node $projectId/$repoName/$fullPath")
        }
    }

    override fun uncompressedNode(nodeUnCompressedRequest: NodeUnCompressedRequest) {
        with(nodeUnCompressedRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().unset(TNode::compressed.name)
                .set(TNode::lastAccessDate.name, LocalDateTime.now())
            nodeDao.updateFirst(query, update)
            logger.info("Success to uncompress node $projectId/$repoName/$fullPath")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeCompressSupport::class.java)
    }
}
