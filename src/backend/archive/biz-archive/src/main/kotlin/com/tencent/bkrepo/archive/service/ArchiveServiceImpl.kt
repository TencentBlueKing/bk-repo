package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.ArchiveFileNotFound
import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.ArchiveMessageCode
import com.tencent.bkrepo.archive.constant.XZ_SUFFIX
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.DeleteObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.RestoreObjectRequest
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 归档服务实现类
 * */
@Service
class ArchiveServiceImpl(
    private val archiveProperties: ArchiveProperties,
    private val archiveFileRepository: ArchiveFileRepository,
) : ArchiveService {

    private val cosClient = CosClient(archiveProperties.cos)
    override fun archive(request: CreateArchiveFileRequest) {
        // created
        with(request) {
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
            if (af != null) {
                // 文件已归档
                if (af.status == ArchiveStatus.ARCHIVED) {
                    return
                }
                af.lastModifiedBy = operator
                af.lastModifiedDate = LocalDateTime.now()
                // 重新触发归档后逻辑，删除原存储文件和更新node状态
                af.status = if (af.status == ArchiveStatus.COMPLETED) ArchiveStatus.ARCHIVED else ArchiveStatus.CREATED
                archiveFileRepository.save(af)
            } else {
                val archiveFile = TArchiveFile(
                    createdBy = operator,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = operator,
                    lastModifiedDate = LocalDateTime.now(),
                    sha256 = sha256,
                    storageCredentialsKey = storageCredentialsKey,
                    size = size,
                    status = ArchiveStatus.CREATED,
                )
                archiveFileRepository.save(archiveFile)
            }
            logger.info("Archive file $sha256 in $storageCredentialsKey.")
        }
    }

    override fun delete(request: ArchiveFileRequest) {
        with(request) {
            val archiveFile = archiveFileRepository.findBySha256AndStorageCredentialsKey(
                sha256,
                storageCredentialsKey,
            ) ?: return
            val key = "$sha256$XZ_SUFFIX"
            val deleteObjectRequest = DeleteObjectRequest(key)
            cosClient.deleteObject(deleteObjectRequest)
            logger.info("Success delete $key on archive cos.")
            archiveFileRepository.delete(archiveFile)
            logger.info("Delete archive file $sha256 in $storageCredentialsKey.")
        }
    }

    override fun restore(request: ArchiveFileRequest) {
        with(request) {
            // 取回限制
            val midnight = LocalDateTime.now().toLocalDate().atStartOfDay()
            val count = archiveFileRepository.countByLastModifiedDateAfterAndStatus(
                midnight,
                ArchiveStatus.WAIT_TO_RESTORE,
            )
            if (count > archiveProperties.restoreLimit) {
                throw ErrorCodeException(ArchiveMessageCode.RESTORE_COUNT_LIMIT)
            }
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            // 只有已经完成归档的文件才允许恢复
            if (af.status == ArchiveStatus.COMPLETED) {
                af.lastModifiedBy = operator
                af.lastModifiedDate = LocalDateTime.now()
                af.status = ArchiveStatus.WAIT_TO_RESTORE
                val key = "${sha256}$XZ_SUFFIX"
                val restoreRequest = RestoreObjectRequest(
                    key = key,
                    days = archiveProperties.days,
                    tier = archiveProperties.tier,
                )
                cosClient.restoreObject(restoreRequest)
                archiveFileRepository.save(af)
                logger.info("Restore archive file $sha256 in $storageCredentialsKey.")
            }
        }
    }

    override fun get(sha256: String, storageCredentialsKey: String?): ArchiveFile? {
        val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
            ?: return null
        return ArchiveFile(
            createdBy = af.createdBy,
            createdDate = af.createdDate,
            lastModifiedBy = af.lastModifiedBy,
            lastModifiedDate = af.lastModifiedDate,
            sha256 = af.sha256,
            size = af.size,
            storageCredentialsKey = af.storageCredentialsKey,
            status = af.status,
        )
    }

    override fun complete(request: ArchiveFileRequest) {
        with(request) {
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            af.lastModifiedBy = operator
            af.lastModifiedDate = LocalDateTime.now()
            af.status = ArchiveStatus.COMPLETED
            archiveFileRepository.save(af)
            logger.info("Complete archive file $sha256 in $storageCredentialsKey.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveServiceImpl::class.java)
    }
}
