package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.model.drive.TDriveBlockNode
import com.tencent.bkrepo.fs.server.repository.RDriveBlockNodeDao
import com.tencent.bkrepo.fs.server.repository.drive.RDriveSnapSeqDao
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
    private val driveSnapSeq: RDriveSnapSeqDao,
) {

    suspend fun createBlock(blockNode: TDriveBlockNode, storageCredentials: StorageCredentials?): TDriveBlockNode {
        with(blockNode) {
            val driveBlock = driveBlockNodeDao.save(blockNode)
            driveFileReferenceService.increment(driveBlock.sha256, storageCredentials?.key)
            logger.info("Create drive block node[$projectId/$repoName/$ino-$startPos], sha256[$sha256] success.")
            return driveBlock
        }
    }

    suspend fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        ino: Long,
        snapSeq: Long? = null,
    ): List<TDriveBlockNode> {
        val criteria = if (snapSeq != null) {
            snapCriteria(projectId, repoName, ino, snapSeq)
        } else {
            curSnapCriteria(projectId, repoName, ino)
        }
        criteria.and(TDriveBlockNode::startPos.name).lte(range.end)
            .and(TDriveBlockNode::endPos.name).gte(range.start)
        val query = Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
        return driveBlockNodeDao.find(query)
    }

    suspend fun listAllBlocks(projectId: String, repoName: String, ino: Long): List<TDriveBlockNode> {
        val criteria = curSnapCriteria(projectId, repoName, ino)
        val query = Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
        return driveBlockNodeDao.find(query)
    }

    suspend fun deleteBlocks(projectId: String, repoName: String, ino: Long, curSnapSeq: Long) {
        val criteria = curSnapCriteria(projectId, repoName, ino)
        val update = Update()
            .set(TDriveBlockNode::deleted.name, LocalDateTime.now())
            .set(TDriveBlockNode::deleteSnapSeq.name, curSnapSeq)
        val result = driveBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Delete ${result.modifiedCount} drive blocks[$ino] at snapSeq[$curSnapSeq] success.")
    }

    suspend fun restoreBlocks(
        projectId: String,
        repoName: String,
        ino: Long,
        curSnapSeq: Long,
        nodeDeleteDate: LocalDateTime
    ) {
        val criteria = deletedCriteria(projectId, repoName, ino, curSnapSeq, nodeDeleteDate)
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
            val criteria = curSnapCriteria(projectId, repoName, ino)
                .and(TDriveBlockNode::startPos.name).`is`(startPos)
                .and(TDriveBlockNode::sha256.name).`is`(sha256)
            return driveBlockNodeDao.exists(Query(criteria))
        }
    }

    suspend fun listDeletedBlocks(
        projectId: String,
        repoName: String,
        ino: Long,
        curSnapSeq: Long,
        nodeDeleteDate: LocalDateTime
    ): List<TDriveBlockNode> {
        val query = Query(deletedCriteria(projectId, repoName, ino, curSnapSeq, nodeDeleteDate))
        return driveBlockNodeDao.find(query)
    }

    private fun curSnapCriteria(projectId: String, repoName: String, ino: Long): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::projectId.name).isEqualTo(projectId)
            .and(TDriveBlockNode::repoName.name).isEqualTo(repoName)
            .and(TDriveBlockNode::deleteSnapSeq.name).`is`(Long.MAX_VALUE)
    }

    /**
     * 查询指定快照可见的块：创建时的snapSeq <= targetSnapSeq 且 deleteSnapSeq > targetSnapSeq
     */
    private fun snapCriteria(projectId: String, repoName: String, ino: Long, snapSeq: Long): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::projectId.name).isEqualTo(projectId)
            .and(TDriveBlockNode::repoName.name).isEqualTo(repoName)
            .and(TDriveBlockNode::snapSeq.name).lte(snapSeq)
            .and(TDriveBlockNode::deleteSnapSeq.name).gt(snapSeq)
    }

    private fun deletedCriteria(
        projectId: String,
        repoName: String,
        ino: Long,
        curSnapSeq: Long,
        nodeDeleteDate: LocalDateTime
    ): Criteria {
        return Criteria.where(TDriveBlockNode::ino.name).`is`(ino)
            .and(TDriveBlockNode::projectId.name).isEqualTo(projectId)
            .and(TDriveBlockNode::repoName.name).isEqualTo(repoName)
            .and(TDriveBlockNode::deleteSnapSeq.name).isEqualTo(curSnapSeq)
            .and(TDriveBlockNode::deleted.name).isEqualTo(nodeDeleteDate)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveBlockNodeService::class.java)
    }
}
