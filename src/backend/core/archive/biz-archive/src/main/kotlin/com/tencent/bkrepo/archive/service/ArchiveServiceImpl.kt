package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.ArchiveFileNotFound
import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.ArchiveMessageCode
import com.tencent.bkrepo.archive.core.FileEntityEvent
import com.tencent.bkrepo.archive.core.archive.ArchiveManager
import com.tencent.bkrepo.archive.core.archive.EmptyArchiver
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
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
    private val archiveManager: ArchiveManager,
    private val storageService: StorageService,
) : ArchiveService {

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
                val properties = archiveProperties.extraCredentialsConfig[archiveCredentialsKey]
                    ?: archiveProperties.defaultCredentials
                val archiveFile = TArchiveFile(
                    createdBy = operator,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = operator,
                    lastModifiedDate = LocalDateTime.now(),
                    sha256 = sha256,
                    storageCredentialsKey = storageCredentialsKey,
                    size = size,
                    status = ArchiveStatus.CREATED,
                    archiver = EmptyArchiver.NAME,
                    archiveCredentialsKey = archiveCredentialsKey,
                    storageClass = properties.storageClass,
                )
                archiveFileRepository.save(archiveFile)
                SpringContextUtils.publishEvent(FileEntityEvent(sha256, archiveFile))
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
            val key = archiveManager.getKey(sha256, archiveFile.archiver)
            val credentials = archiveManager.getStorageCredentials(archiveFile.archiveCredentialsKey)
            storageService.delete(key, credentials)
            logger.info("Success delete $key on ${credentials.key}.")
            archiveFileRepository.delete(archiveFile)
            logger.info("Delete archive file $sha256 in $storageCredentialsKey.")
        }
    }

    override fun restore(request: ArchiveFileRequest) {
        with(request) {
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            val properties = archiveProperties.extraCredentialsConfig[af.archiveCredentialsKey]
                ?: archiveProperties.defaultCredentials
            // 取回限制
            val midnight = LocalDateTime.now().toLocalDate().atStartOfDay()
            val count = archiveFileRepository.countByLastModifiedDateAfterAndStatus(
                midnight,
                ArchiveStatus.WAIT_TO_RESTORE,
            )
            if (count > properties.restoreLimit) {
                throw ErrorCodeException(ArchiveMessageCode.RESTORE_COUNT_LIMIT)
            }

            // 只有已经完成归档的文件才允许恢复
            if (af.status == ArchiveStatus.COMPLETED) {
                af.lastModifiedBy = operator
                af.lastModifiedDate = LocalDateTime.now()
                af.status = ArchiveStatus.WAIT_TO_RESTORE
                val key = archiveManager.getKey(sha256, af.archiver)
                storageService.restore(
                    key,
                    properties.days,
                    properties.tier.name,
                    archiveManager.getStorageCredentials(af.archiveCredentialsKey),
                )
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
            archiver = af.archiver,
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
