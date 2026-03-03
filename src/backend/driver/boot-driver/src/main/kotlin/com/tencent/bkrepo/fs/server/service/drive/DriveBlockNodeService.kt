package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.metadata.model.TDriveBlockNode
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.repository.RDriveBlockNodeDao
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DriveBlockNodeService(
    private val driveBlockNodeDao: RDriveBlockNodeDao,
    private val driveFileReferenceService: DriveFileReferenceService
) {

    suspend fun createBlock(blockNode: TDriveBlockNode, storageCredentials: StorageCredentials?): TDriveBlockNode {
        with(blockNode) {
            val driveBlock = driveBlockNodeDao.save(blockNode)
            driveFileReferenceService.increment(driveBlock.sha256, storageCredentials?.key)
            logger.info("Create drive block node[$projectId/$repoName/$ino-$startPos], sha256[$sha256] success.")
            return driveBlock
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveBlockNodeService::class.java)
    }
}
