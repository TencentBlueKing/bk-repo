package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.RESOURCE_EXISTED
import com.tencent.bkrepo.common.api.message.CommonMessageCode.RESOURCE_NOT_FOUND
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapshot
import com.tencent.bkrepo.fs.server.repository.drive.RDriveSnapshotDao
import com.tencent.bkrepo.fs.server.response.drive.DriveSnapshot
import com.tencent.bkrepo.fs.server.response.drive.toDriveSnapshot
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Drive 快照服务
 */
@Service
class DriveSnapshotService(
    private val driveSnapshotDao: RDriveSnapshotDao,
    private val driveSnapSeqService: DriveSnapSeqService,
    private val driveProperties: DriveProperties,
) {
    suspend fun createSnapshot(
        projectId: String,
        repoName: String,
        name: String,
        description: String?
    ): DriveSnapshot {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validateName(name, TDriveSnapshot::name.name)
        DriveServiceUtils.validateLength(name, TDriveSnapshot::name.name, driveProperties.nameMaxLength)
        DriveServiceUtils.validateLength(
            description,
            TDriveSnapshot::description.name,
            driveProperties.descriptionMaxLength
        )
        val currentSnapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName, fromCache = false)
        val now = LocalDateTime.now()
        val operator = ReactiveSecurityUtils.getUser()
        val snapshot = TDriveSnapshot(
            id = null,
            createdBy = operator,
            createdDate = now,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            projectId = projectId,
            repoName = repoName,
            name = name,
            description = description,
            snapSeq = currentSnapSeq,
        )
        val created = try {
            driveSnapshotDao.insert(snapshot)
        } catch (_: DuplicateKeyException) {
            // 可能出现“快照插入成功但 snapSeq 未递增”的异常场景，冲突时尝试补偿推进序号。
            try {
                driveSnapSeqService.incSnapSeq(projectId, repoName, currentSnapSeq)
                logger.warn("try compensate snapSeq[$projectId/$repoName/$currentSnapSeq] increase success.")
            } catch (e: Exception) {
                logger.warn("try compensate snapSeq[$projectId/$repoName/$currentSnapSeq] increase failed.", e)
            }
            throw ErrorCodeException(RESOURCE_EXISTED, "drive snapshot[$projectId/$repoName/$currentSnapSeq]")
        }
        driveSnapSeqService.incSnapSeq(projectId, repoName, currentSnapSeq)
        logger.info("Create drive snapshot[$projectId/$repoName/${created.snapSeq}] success.")
        return created.toDriveSnapshot()
    }

    suspend fun listSnapshotsPage(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<DriveSnapshot> {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validatePage(pageNumber, pageSize, driveProperties.listCountLimit)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val (records, totalRecords) = driveSnapshotDao.page(projectId, repoName, pageRequest)
        return Pages.ofResponse(pageRequest, totalRecords, records.map { it.toDriveSnapshot() })
    }

    suspend fun updateSnapshot(
        projectId: String,
        repoName: String,
        snapshotId: String,
        name: String?,
        description: String?,
    ): DriveSnapshot {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        Preconditions.checkArgument(snapshotId.isNotBlank(), "snapshotId")
        if (name == null && description == null) {
            val existing = driveSnapshotDao.find(projectId, repoName, snapshotId)
                ?: throw ErrorCodeException(RESOURCE_NOT_FOUND, "drive snapshot[$projectId/$repoName/$snapshotId]")
            logger.info("Skip update drive snapshot[$projectId/$repoName/$snapshotId], all fields are null.")
            return existing.toDriveSnapshot()
        }
        name?.let { DriveServiceUtils.validateName(it, TDriveSnapshot::name.name) }
        DriveServiceUtils.validateLength(name, TDriveSnapshot::name.name, driveProperties.nameMaxLength)
        DriveServiceUtils.validateLength(
            description,
            TDriveSnapshot::description.name,
            driveProperties.descriptionMaxLength
        )
        val operator = DriveServiceUtils.getUserOrSystem()
        val updateResult = driveSnapshotDao.updateNameAndDescription(
            projectId = projectId,
            repoName = repoName,
            id = snapshotId,
            name = name,
            description = description,
            operator = operator,
        )
        if (updateResult.matchedCount != 1L) {
            throw ErrorCodeException(RESOURCE_NOT_FOUND, "drive snapshot[$projectId/$repoName/$snapshotId]")
        }
        val updated = driveSnapshotDao.find(projectId, repoName, snapshotId)
            ?: throw ErrorCodeException(RESOURCE_NOT_FOUND, "drive snapshot[$projectId/$repoName/$snapshotId]")
        logger.info("Update drive snapshot[$projectId/$repoName/$snapshotId] success.")
        return updated.toDriveSnapshot()
    }

    suspend fun deleteSnapshot(projectId: String, repoName: String, snapshotId: String) {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        Preconditions.checkArgument(snapshotId.isNotBlank(), "snapshotId")
        val deleteResult = driveSnapshotDao.delete(
            projectId = projectId,
            repoName = repoName,
            id = snapshotId,
        )
        if (deleteResult.deletedCount != 1L) {
            throw ErrorCodeException(RESOURCE_NOT_FOUND, "drive snapshot[$projectId/$repoName/$snapshotId]")
        }
        logger.info("Delete drive snapshot[$projectId/$repoName/$snapshotId] success.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveSnapshotService::class.java)
    }
}
