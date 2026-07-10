package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import org.bson.types.ObjectId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.MessageDigest

@Component
class DriveInoAllocator(
    private val driveNodeDao: RDriveNodeDao,
) {
    suspend fun allocate(projectId: String, repoName: String): Long {
        repeat(MAX_RETRY) {
            val nodeId = ObjectId().toHexString()
            val ino = inoFromNodeId(nodeId)
            if (!driveNodeDao.existsIno(projectId, repoName, ino)) {
                return ino
            }
        }
        throw DuplicateKeyException("Failed to allocate drive ino for [$projectId/$repoName]")
    }

    companion object {
        private const val MAX_RETRY = 5

        fun inoFromNodeId(nodeId: String): Long {
            val digest = MessageDigest.getInstance(SHA_256).digest(nodeId.toByteArray(Charsets.UTF_8))
            val hash = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
            val range = Long.MAX_VALUE - DriveNodeQueryHelper.ROOT_INO
            return DriveNodeQueryHelper.ROOT_INO + (hash % range)
        }

        private const val SHA_256 = "SHA-256"
    }
}
