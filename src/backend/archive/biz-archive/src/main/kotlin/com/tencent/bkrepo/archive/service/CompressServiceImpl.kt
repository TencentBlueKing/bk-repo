package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.ArchiveFileNotFound
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 压缩服务实现类
 * */
@Service
class CompressServiceImpl(
    private val compressFileRepository: CompressFileRepository,
    private val storageService: StorageService,
    private val fileReferenceClient: FileReferenceClient,
) : CompressService {

    override fun compress(request: CompressFileRequest) {
        with(request) {
            // 队头元素
            val head = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
            if (head != null && head.status != CompressStatus.NONE) {
                // 压缩任务已存在
                logger.info("Compress file [$sha256] already exists，status: ${head.status}.")
                head.lastModifiedBy = operator
                head.lastModifiedDate = LocalDateTime.now()
                // 重新触发压缩后逻辑，删除原存储文件和更新node状态
                head.status = if (head.status == CompressStatus.COMPLETED) {
                    CompressStatus.COMPRESSED
                } else {
                    CompressStatus.CREATED
                }
                return
            }
            var currentChainLength = 0
            // 这是队头
            if (head != null) {
                currentChainLength = head.chainLength + 1
                // 超出链最大长度限制
                if (currentChainLength > MAX_CHAIN_LENGTH) {
                    // 超出队列长度
                    logger.info("Exceed max chain length,ignore it.")
                    return
                }
                // 删除旧头
                compressFileRepository.delete(head)
            }
            val compressFile = TCompressFile(
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                sha256 = sha256,
                baseSha256 = baseSha256,
                uncompressedSize = size,
                storageCredentialsKey = storageCredentialsKey,
                status = CompressStatus.CREATED,
            )
            compressFileRepository.save(compressFile)
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
            /*
            * 确定新链长度，取最长链长度
            * 1. sha256链长度+1
            * 2. baseSha256所在链长
            * */
            val newChainLength = maxOf(newChain.chainLength, currentChainLength)
            newChain.chainLength = newChainLength
            compressFileRepository.save(newChain)
            fileReferenceClient.increment(baseSha256, storageCredentialsKey)
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
                logger.info("Uncompress file [$sha256] on $storageCredentialsKey.")
            }
        }
    }

    override fun delete(request: DeleteCompressRequest) {
        with(request) {
            val file = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, storageCredentialsKey)
                ?: return
            if (file.status != CompressStatus.NONE) {
                val storageCredentials = ArchiveUtils.getStorageCredentials(storageCredentialsKey)
                if (storageService.isCompressed(sha256, storageCredentials)) {
                    storageService.deleteCompressed(sha256, storageCredentials)
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

    companion object {
        private val logger = LoggerFactory.getLogger(CompressServiceImpl::class.java)
        private const val MAX_CHAIN_LENGTH = 10
    }
}
