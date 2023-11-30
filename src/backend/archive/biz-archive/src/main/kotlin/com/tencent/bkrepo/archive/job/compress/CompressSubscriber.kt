package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.event.StorageFileCompressedEvent
import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class CompressSubscriber(
    private val compressFileDao: CompressFileDao,
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
) : BaseJobSubscriber<TCompressFile>() {

    override fun doOnNext(value: TCompressFile) {
        with(value) {
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                value,
                TCompressFile::status.name,
                CompressStatus.CREATED.name,
                CompressStatus.COMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start compress.")
                return
            }
            // 压缩
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            var compressedSize = -1L
            try {
                val throughput = measureThroughput(uncompressedSize) {
                    compressedSize = storageService.compress(sha256, baseSha256, credentials, true)
                }
                if (compressedSize == -1L) {
                    return
                }
                // 更新状态
                value.compressedSize = compressedSize
                value.status = CompressStatus.COMPRESSED
                value.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(value)
                val event = StorageFileCompressedEvent(
                    sha256 = sha256,
                    baseSha256 = baseSha256,
                    uncompressed = uncompressedSize,
                    compressed = compressedSize,
                    storageCredentialsKey = storageCredentialsKey,
                    throughput = throughput,
                )
                SpringContextUtils.publishEvent(event)
            } catch (e: Exception) {
                value.status = CompressStatus.COMPRESS_FAILED
                value.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(value)
                throw e
            }
        }
    }

    override fun getSize(value: TCompressFile): Long {
        return value.uncompressedSize
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompressSubscriber::class.java)
    }
}
