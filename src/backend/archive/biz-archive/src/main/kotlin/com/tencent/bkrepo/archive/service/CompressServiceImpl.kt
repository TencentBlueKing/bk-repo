package com.tencent.bkrepo.archive.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.ArchiveFileNotFound
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.constant.ArchiveMessageCode
import com.tencent.bkrepo.archive.event.StorageFileCompressedEvent
import com.tencent.bkrepo.archive.event.StorageFileUncompressedEvent
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.pojo.CompressFile
import com.tencent.bkrepo.archive.repository.CompressFileDao
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.archive.utils.ArchiveDaoUtils.optimisticLock
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.bksync.transfer.exception.TooLowerReuseRateException
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.LocalDateTime

/**
 * 压缩服务实现类
 * */
@Suppress("LeakingThis")
@Service
class CompressServiceImpl(
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
    private val fileReferenceClient: FileReferenceClient,
    private val compressFileDao: CompressFileDao,
    archiveProperties: ArchiveProperties,
) : CompressService {

    private val compressSink = Sinks.many().unicast().onBackpressureBuffer<TCompressFile>()
    private val uncompressSink = Sinks.many().unicast().onBackpressureBuffer<TCompressFile>()

    init {
        val executor = ArchiveUtils.newFixedAndCachedThreadPool(
            archiveProperties.ioThreads,
            ThreadFactoryBuilder().setNameFormat("compress-worker-%d").build(),
        )
        val scheduler = Schedulers.fromExecutor(executor)
        compressSink.asFlux().parallel().runOn(scheduler).subscribe(this::compress0)
        uncompressSink.asFlux().parallel().runOn(scheduler).subscribe(this::uncompress0)
    }

    override fun compress(request: CompressFileRequest) {
        with(request) {
            // 队头元素
            val head = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
            if (head != null && head.status != CompressStatus.NONE) {
                // 压缩任务已存在
                logger.info("Compress file [$sha256] already exists，status: ${head.status}.")
                // 重新触发压缩后逻辑，删除原存储文件和更新node状态
                if (head.status == CompressStatus.COMPLETED) {
                    head.lastModifiedBy = operator
                    head.lastModifiedDate = LocalDateTime.now()
                    head.status = CompressStatus.COMPRESSED
                    compressFileRepository.save(head)
                }
                return
            }
            var currentChainLength = 0
            // 这是队头
            if (head != null) {
                currentChainLength = head.chainLength + 1
                // 超出链最大长度限制
                if (currentChainLength > MAX_CHAIN_LENGTH) {
                    throw ErrorCodeException(ArchiveMessageCode.EXCEED_MAX_CHAIN_LENGTH)
                }
                // 删除旧头
                compressFileRepository.delete(head)
            }
            val newChain = compressFileRepository.findBySha256AndStorageCredentialsKey(
                baseSha256,
                storageCredentialsKey,
            ) ?: TCompressFile(
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                sha256 = baseSha256,
                baseSha256 = "",
                uncompressedSize = size,
                storageCredentialsKey = storageCredentialsKey,
                status = CompressStatus.NONE,
                chainLength = 1,
            )
            if (newChain.status != CompressStatus.NONE) {
                throw ErrorCodeException(ArchiveMessageCode.BASE_COMPRESSED)
            }
            /*
            * 确定新链长度，取最长链长度
            * 1. sha256链长度+1
            * 2. baseSha256所在链长
            * */
            val newChainLength = maxOf(newChain.chainLength, currentChainLength)
            newChain.chainLength = newChainLength
            compressFileRepository.save(newChain)
            val compressFile = TCompressFile(
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                sha256 = sha256,
                baseSha256 = baseSha256,
                baseSize = baseSize,
                uncompressedSize = size,
                storageCredentialsKey = storageCredentialsKey,
                status = CompressStatus.CREATED,
            )
            compressFileRepository.save(compressFile)
            fileReferenceClient.increment(baseSha256, storageCredentialsKey)
            compress(compressFile)
            logger.info("Compress file [$sha256] on $storageCredentialsKey.")
        }
    }

    override fun uncompress(request: UncompressFileRequest) {
        with(request) {
            val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            if (file.status == CompressStatus.COMPLETED || file.status == CompressStatus.COMPRESSED) {
                file.status = CompressStatus.WAIT_TO_UNCOMPRESS
                file.lastModifiedBy = operator
                file.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(file)
                uncompress(file)
                logger.info("Uncompress file [$sha256] on $storageCredentialsKey.")
            }
        }
    }

    override fun delete(request: DeleteCompressRequest) {
        with(request) {
            val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: return
            if (file.status == CompressStatus.NONE) {
                return
            }
            if (file.status != CompressStatus.COMPRESS_FAILED) {
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                if (storageService.isCompressed(sha256, storageCredentials)) {
                    storageService.deleteCompressed(sha256, storageCredentials)
                }
                // 压缩失败的已经解除了base sha256的引用
                fileReferenceClient.decrement(file.baseSha256, storageCredentialsKey)
            }
            /*
            * 解压是小概率事件，所以这里链长度我们就不减少，这样带来的问题是，
            * 压缩链更容易达到最大长度。但是这个影响并不重要。
            * */
            compressFileRepository.delete(file)
            logger.info("Delete compress file [$sha256].")
        }
    }

    override fun complete(request: CompleteCompressRequest) {
        with(request) {
            val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            if (file.status == CompressStatus.COMPRESSED) {
                file.status = CompressStatus.COMPLETED
                file.lastModifiedBy = operator
                file.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(file)
                logger.info("Complete compress file [$sha256].")
            }
        }
    }

    override fun getCompressInfo(sha256: String, storageCredentialsKey: String?): CompressFile? {
        val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
        if (file == null || file.status == CompressStatus.NONE) {
            return null
        }
        with(file) {
            return CompressFile(
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate,
                sha256 = sha256,
                baseSha256 = baseSha256,
                status = status,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                storageCredentialsKey = storageCredentialsKey,
            )
        }
    }

    override fun compress(file: TCompressFile) {
        val result = compressSink.tryEmitNext(file)
        logger.info("Emit file ${file.sha256} to compress: $result")
    }

    override fun uncompress(file: TCompressFile) {
        val result = uncompressSink.tryEmitNext(file)
        logger.info("Emit file ${file.sha256} to uncompress: $result")
    }

    private fun compress0(file: TCompressFile) {
        with(file) {
            logger.info("Start compress file [$sha256].")
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                file,
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
                    compressedSize = retry(
                        times = RETRY_TIMES,
                        delayInSeconds = 1,
                        ignoreExceptions = listOf(TooLowerReuseRateException::class.java),
                    ) {
                        storageService.compress(sha256, uncompressedSize, baseSha256, baseSize, credentials, true)
                    }
                }
                if (compressedSize == -1L) {
                    return
                }
                // 更新状态
                file.compressedSize = compressedSize
                file.status = CompressStatus.COMPRESSED
                file.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(file)
                val event = StorageFileCompressedEvent(
                    sha256 = sha256,
                    baseSha256 = baseSha256,
                    uncompressed = uncompressedSize,
                    compressed = compressedSize,
                    storageCredentialsKey = storageCredentialsKey,
                    throughput = throughput,
                )
                SpringContextUtils.publishEvent(event)
            } catch (e: TooLowerReuseRateException) {
                logger.info("Reuse rate is too lower.")
                compressFailed(file)
            } catch (e: Exception) {
                compressFailed(file)
                throw e
            }
        }
    }

    private fun uncompress0(file: TCompressFile) {
        with(file) {
            logger.info("Start uncompress file [$sha256].")
            // 乐观锁
            val tryLock = compressFileDao.optimisticLock(
                file,
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
            compressFileRepository.findBySha256AndStorageCredentialsKey(baseSha256, storageCredentialsKey)?.let {
                if (file.status == CompressStatus.COMPLETED || file.status == CompressStatus.COMPRESSED) {
                    uncompress0(it)
                }
            }
            try {
                var ret = 0
                val throughput = measureThroughput(uncompressedSize) {
                    ret = storageService.uncompress(sha256, compressedSize, baseSha256, baseSize, credentials)
                }
                if (ret == 0) {
                    return
                }
                // 更新状态
                file.status = CompressStatus.UNCOMPRESSED
                file.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(file)
                val event = StorageFileUncompressedEvent(
                    sha256 = sha256,
                    compressed = compressedSize,
                    uncompressed = uncompressedSize,
                    storageCredentialsKey = storageCredentialsKey,
                    throughput = throughput,
                )
                SpringContextUtils.publishEvent(event)
            } catch (e: Exception) {
                file.status = CompressStatus.UNCOMPRESS_FAILED
                file.lastModifiedDate = LocalDateTime.now()
                compressFileRepository.save(file)
                throw e
            }
        }
    }

    private fun compressFailed(file: TCompressFile) {
        with(file) {
            status = CompressStatus.COMPRESS_FAILED
            lastModifiedDate = LocalDateTime.now()
            compressFileRepository.save(file)
            fileReferenceClient.decrement(baseSha256, storageCredentialsKey)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompressServiceImpl::class.java)
        private const val MAX_CHAIN_LENGTH = 10
        private const val RETRY_TIMES = 3
    }
}
