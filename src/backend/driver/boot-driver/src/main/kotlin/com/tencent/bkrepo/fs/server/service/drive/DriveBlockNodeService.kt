package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.dao.node.DriveSnapSeqDao
import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.repository.RDriveBlockNodeDao
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DriveBlockNodeService(
    private val driveBlockNodeDao: RDriveBlockNodeDao,
    private val driveFileReferenceService: DriveFileReferenceService,
    private val driveSnapSeq: DriveSnapSeqDao,
) {

    suspend fun createBlock(blockNode: TDriveBlockNode, storageCredentials: StorageCredentials?): TDriveBlockNode {
        with(blockNode) {
            val driveBlock = driveBlockNodeDao.save(blockNode)
            driveFileReferenceService.increment(driveBlock.sha256, storageCredentials?.key)
            logger.info("Create drive block node[$projectId/$repoName/$ino-$startPos], sha256[$sha256] success.")
            return driveBlock
        }
    }

    suspend fun listBlocks(range: Range, ino: String): List<TDriveBlockNode> {
        val criteria = curSnapCriteria(ino)
            .and(TDriveBlockNode::startPos.name).lte(range.end)
            .and(TDriveBlockNode::endPos.name).gte(range.start)
        val query = Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
        return driveBlockNodeDao.find(query)
    }

    suspend fun listAllBlocks(ino: String): List<TDriveBlockNode> {
        val query = Query(curSnapCriteria(ino)).with(Sort.by(TDriveBlockNode::createdDate.name))
        return driveBlockNodeDao.find(query)
    }

    suspend fun deleteBlocks(ino: String, curSnapSeq: Long) {
        val criteria = curSnapCriteria(ino)
        val update = Update()
            .set(TDriveBlockNode::deleted.name, LocalDateTime.now())
            .set(TDriveBlockNode::deleteSnapSeq.name, curSnapSeq)
        val result = driveBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Delete ${result.modifiedCount} drive blocks[$ino] at snapSeq[$curSnapSeq] success.")
    }

    suspend fun restoreBlocks(ino: String, curSnapSeq: Long, nodeDeleteDate: LocalDateTime) {
        val criteria = deletedCriteria(ino, curSnapSeq, nodeDeleteDate)
        val update = Update()
            .set(TDriveBlockNode::deleted.name, null)
            .set(TDriveBlockNode::deleteSnapSeq.name, Long.MAX_VALUE)
        val result = driveBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info(
            "Restore ${result.modifiedCount} drive blocks[$ino] at snap[$curSnapSeq] and time[$nodeDeleteDate] success."
        )
    }

    suspend fun checkBlockExist(blockNode: TDriveBlockNode): Boolean {
        with(blockNode) {
            val criteria = curSnapCriteria(ino)
                .and(TDriveBlockNode::startPos.name).`is`(startPos)
                .and(TDriveBlockNode::sha256.name).`is`(sha256)
            return driveBlockNodeDao.exists(Query(criteria))
        }
    }

    suspend fun listDeletedBlocks(
        ino: String,
        curSnapSeq: Long,
        nodeDeleteDate: LocalDateTime
    ): List<TDriveBlockNode> {
        val query = Query(deletedCriteria(ino, curSnapSeq, nodeDeleteDate))
        return driveBlockNodeDao.find(query)
    }

    private fun curSnapCriteria(ino: String): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::deleteSnapSeq.name).`is`(Long.MAX_VALUE)
    }

    private fun deletedCriteria(ino: String, curSnapSeq: Long, nodeDeleteDate: LocalDateTime): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::deleteSnapSeq.name).isEqualTo(curSnapSeq)
            .and(TDriveBlockNode::deleted.name).isEqualTo(nodeDeleteDate)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveBlockNodeService::class.java)
    }
}
