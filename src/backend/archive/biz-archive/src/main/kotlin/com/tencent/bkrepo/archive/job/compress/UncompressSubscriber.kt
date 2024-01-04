package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.event.StorageFileUncompressedEvent
import com.tencent.bkrepo.archive.job.AsyncBaseJobSubscriber
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadPoolExecutor

class UncompressSubscriber(
    private val compressFileDao: CompressFileDao,
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
    executor: ThreadPoolExecutor,
) : AsyncBaseJobSubscriber<TCompressFile>(executor) {

    override fun doOnNext(value: TCompressFile) {
        with(value) {
            logger.info("Start uncompress file [$sha256].")
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                value,
                TCompressFile::status.name,
                CompressStatus.WAIT_TO_UNCOMPRESS.name,
                CompressStatus.UNCOMPRESSING.name,
            )
            if (!tryLock) {
                logger.info("File[$sha256] already start uncompress.")
                return
            }
            // 解压
            val credentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
            try {
                var ret = 0
                val throughput = measureThroughput(uncompressedSize) {
                    ret = storageService.uncompress(sha256, credentials)
                }
                if (ret == 0) {
                    return
                }
                // 更新状态
                value.status = CompressStatus.UNCOMPRESSED
                value.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(value)
                val event = StorageFileUncompressedEvent(
                    sha256 = sha256,
                    compressed = compressedSize,
                    uncompressed = uncompressedSize,
                    storageCredentialsKey = storageCredentialsKey,
                    throughput = throughput,
                )
                SpringContextUtils.publishEvent(event)
            } catch (e: Exception) {
                value.status = CompressStatus.UNCOMPRESS_FAILED
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
        private val logger = LoggerFactory.getLogger(UncompressSubscriber::class.java)
    }
}
