package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveFileReference
import com.tencent.bkrepo.common.metadata.pojo.file.FileReference
import com.tencent.bkrepo.common.metadata.util.FileReferenceQueryHelper
import com.tencent.bkrepo.fs.server.repository.RDriveFileReferenceDao
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * Drive 文件引用服务实现类
 */
@Service
@Conditional(ReactiveCondition::class)
class DriveFileReferenceService(
    private val driveFileReferenceDao: RDriveFileReferenceDao,
) {

    suspend fun increment(sha256: String, credentialsKey: String?, inc: Long = 1L): Boolean {
        val query = FileReferenceQueryHelper.buildQuery(sha256, credentialsKey)
        val update = Update().inc(TDriveFileReference::count.name, inc)
        try {
            driveFileReferenceDao.upsert(query, update)
        } catch (exception: DuplicateKeyException) {
            // retry because upsert operation is not atomic
            driveFileReferenceDao.upsert(query, update)
        }
        logger.info("Increment $inc reference of drive file [$sha256] on credentialsKey [$credentialsKey].")
        return true
    }

    suspend fun decrement(sha256: String, credentialsKey: String?): Boolean {
        val query = FileReferenceQueryHelper.buildQuery(sha256, credentialsKey, 0)
        val update = Update().apply { inc(TDriveFileReference::count.name, -1) }
        val result = driveFileReferenceDao.updateFirst(query, update)

        if (result.modifiedCount == 1L) {
            logger.info("Decrement references of drive file [$sha256] on credentialsKey [$credentialsKey].")
            return true
        }

        driveFileReferenceDao.findOne(FileReferenceQueryHelper.buildQuery(sha256, credentialsKey)) ?: run {
            logger.error("Failed to decrement reference of drive file [$sha256] on credentialsKey [$credentialsKey]")
            return false
        }

        logger.error(
            "Failed to decrement reference of drive file [$sha256] on credentialsKey [$credentialsKey]: " +
                "reference count is 0."
        )
        return false
    }

    suspend fun count(sha256: String, credentialsKey: String?): Long {
        val query = FileReferenceQueryHelper.buildQuery(sha256, credentialsKey)
        return driveFileReferenceDao.findOne(query)?.count ?: 0
    }

    suspend fun get(credentialsKey: String?, sha256: String): FileReference {
        val query = FileReferenceQueryHelper.buildQuery(sha256, credentialsKey)
        val tDriveFileReference = driveFileReferenceDao.findOne(query)
            ?: throw NotFoundException(ArtifactMessageCode.NODE_NOT_FOUND)
        return convert(tDriveFileReference)
    }

    suspend fun exists(sha256: String, credentialsKey: String?): Boolean {
        return driveFileReferenceDao.exists(FileReferenceQueryHelper.buildQuery(sha256, credentialsKey))
    }

    private fun convert(tDriveFileReference: TDriveFileReference): FileReference {
        return tDriveFileReference.run { FileReference(sha256, credentialsKey, count) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveFileReferenceService::class.java)
    }
}
