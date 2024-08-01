package com.tencent.bkrepo.common.metadata.service.blocknode.impl

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.common.metadata.dao.RBlockNodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.RBlockNodeService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.gt
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

abstract class RAbstractBlockNodeService(
    private val rBlockNodeDao: RBlockNodeDao
) : RBlockNodeService {

    override suspend fun createBlock(blockNode: TBlockNode, storageCredentials: StorageCredentials?): TBlockNode {
        with(blockNode) {
            val bn = rBlockNodeDao.save(blockNode)
            incFileRef(bn.sha256, storageCredentials?.key)
            logger.info("Create block node[$projectId/$repoName$nodeFullPath-$startPos] ,sha256[$sha256] success.")
            return bn
        }
    }

    override suspend fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String
    ): List<TBlockNode> {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::deleted).isEqualTo(null)
            .and(TBlockNode::createdDate).gt(LocalDateTime.parse(createdDate))
            .norOperator(
                TBlockNode::startPos.gt(range.end),
                TBlockNode::endPos.lt(range.start)
            )
        val query = Query(criteria).with(Sort.by(TBlockNode::createdDate.name))
        return rBlockNodeDao.find(query)
    }

    override suspend fun deleteBlocks(
        projectId: String,
        repoName: String,
        fullPath: String
    ) {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::deleted).isEqualTo(null)
        val update = Update().set(TBlockNode::deleted.name, LocalDateTime.now())
        rBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Delete node blocks[$projectId/$repoName$fullPath] success.")
    }

    override suspend fun moveBlocks(projectId: String, repoName: String, fullPath: String, dstFullPath: String) {
        val nodeDetail = getNodeDetail(projectId, repoName, dstFullPath)
        if (nodeDetail.folder) {
            val criteria = where(TBlockNode::nodeFullPath).regex("^${EscapeUtils.escapeRegex(fullPath)}/")
                .and(TBlockNode::projectId.name).isEqualTo(projectId)
                .and(TBlockNode::repoName.name).isEqualTo(repoName)
                .and(TBlockNode::deleted).isEqualTo(null)
            val blocks = rBlockNodeDao.find(Query(criteria))
            blocks.forEach {
                val update = Update().set(TBlockNode::nodeFullPath.name, it.nodeFullPath.replace(fullPath, dstFullPath))
                val query = Query(Criteria.where(ID).isEqualTo(it.id).and(TBlockNode::repoName).isEqualTo(repoName))
                rBlockNodeDao.updateMulti(query, update)
            }
        } else {
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
                .and(TBlockNode::projectId.name).isEqualTo(projectId)
                .and(TBlockNode::repoName.name).isEqualTo(repoName)
                .and(TBlockNode::deleted).isEqualTo(null)
            val update = Update().set(TBlockNode::nodeFullPath.name, dstFullPath)
            rBlockNodeDao.updateMulti(Query(criteria), update)
        }
        logger.info("Move node[$projectId/$repoName$fullPath] to node[$projectId/$repoName$dstFullPath] success.")
    }



    override suspend fun restoreBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime
    ) {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::createdDate).gt(nodeCreateDate).lt(nodeDeleteDate)
        val update = Update().set(TBlockNode::deleted.name, null)
        val result = rBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Restore ${result.modifiedCount} blocks node[$projectId/$repoName$fullPath] " +
            "between $nodeCreateDate and $nodeDeleteDate success.")
    }

    abstract suspend fun incFileRef(sha256: String, credentialsKey: String?)

    abstract suspend fun getNodeDetail(projectId: String, repoName: String, dstFullPath: String): NodeDetail

    companion object {
        private val logger = LoggerFactory.getLogger(RAbstractBlockNodeService::class.java)
    }
}
