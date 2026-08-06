package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.drive.TDriveBlockNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveBlockNodeQueryHelper
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.repository.drive.RDriveBlockNodeDao
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DriveBlockNodeService(
    private val driveBlockNodeDao: RDriveBlockNodeDao,
    private val driveFileReferenceService: DriveFileReferenceService,
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
        createdDate: LocalDateTime,
        snapSeq: Long? = null,
    ): List<TDriveBlockNode> {
        val query = DriveBlockNodeQueryHelper.listBlocksQuery(range, projectId, repoName, ino, snapSeq)
        return driveBlockNodeDao.find(query)
    }

    suspend fun listAllBlocks(projectId: String, repoName: String, ino: Long): List<TDriveBlockNode> {
        val criteria = DriveBlockNodeQueryHelper.curSnapCriteria(projectId, repoName, ino)
        val query = Query(criteria).with(Sort.by(TDriveBlockNode::createdDate.name))
        return driveBlockNodeDao.find(query)
    }

    suspend fun checkBlockExist(blockNode: TDriveBlockNode): Boolean {
        with(blockNode) {
            val criteria = DriveBlockNodeQueryHelper.curSnapCriteria(projectId, repoName, ino)
                .and(TDriveBlockNode::startPos.name).`is`(startPos)
                .and(TDriveBlockNode::sha256.name).`is`(sha256)
            return driveBlockNodeDao.exists(Query(criteria))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveBlockNodeService::class.java)
    }
}
