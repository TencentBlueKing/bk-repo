package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.ArchiveFileNotFound
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.constant.ArchiveMessageCode
import com.tencent.bkrepo.archive.constant.MAX_CHAIN_LENGTH
import com.tencent.bkrepo.archive.job.compress.BDZipManager
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.pojo.CompressFile
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.archive.request.UpdateCompressFileStatusRequest
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 压缩服务实现类
 * */
@Service
class CompressServiceImpl(
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
    private val fileReferenceClient: FileReferenceClient,
    private val bdZipManager: BDZipManager,
) : CompressService {

    private val compressSink = Sinks.many().unicast().onBackpressureBuffer<TCompressFile>()
    private val uncompressSink = Sinks.many().unicast().onBackpressureBuffer<TCompressFile>()
    private val compressDisposable = compressSink.asFlux().subscribe(bdZipManager::compress)
    private val uncompressDisposable = uncompressSink.asFlux().subscribe(bdZipManager::uncompress)
    private var shutdown = AtomicBoolean(false)

    override fun compress(request: CompressFileRequest) {
        with(request) {
            require(sha256 != baseSha256)
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
            if (newChain.status != CompressStatus.NONE && newChain.status != CompressStatus.COMPRESS_FAILED) {
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

    override fun updateStatus(request: UpdateCompressFileStatusRequest) {
        with(request) {
            val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: throw ArchiveFileNotFound(sha256)
            val oldStatus = file.status
            file.status = status
            compressFileRepository.save(file)
            if (status == CompressStatus.CREATED) {
                compress(file)
            }
            if (status == CompressStatus.WAIT_TO_UNCOMPRESS) {
                uncompress(file)
            }
            logger.info("Update file $sha256 status $oldStatus -> $status.")
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

    override fun cancel() {
        if (shutdown.compareAndSet(false, true)) {
            compressDisposable?.dispose()
            uncompressDisposable?.dispose()
            logger.info("Shutdown compress service successful.")
        } else {
            logger.info("Compress service has been shutdown.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompressServiceImpl::class.java)
    }
}
