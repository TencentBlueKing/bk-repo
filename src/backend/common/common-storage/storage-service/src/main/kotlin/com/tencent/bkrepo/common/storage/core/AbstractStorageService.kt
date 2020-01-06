package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.locator.FileLocator
import com.tencent.bkrepo.common.storage.message.StorageException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

/**
 * 存储服务抽象实现
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
abstract class AbstractStorageService : StorageService {

    @Autowired
    private lateinit var fileLocator: FileLocator

    @Autowired
    protected lateinit var fileStorage: FileStorage

    @Autowired
    private lateinit var storageProperties: StorageProperties

    private val tempFileClient: FileSystemClient by lazy { FileSystemClient(determineTempPath()) }

    override fun store(digest: String, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?) {
        val path = fileLocator.locate(digest)
        val credentials = getCredentialsOrDefault(storageCredentials)

        try {
            if (doExist(path, digest, credentials)) {
                logger.debug("File [$digest] exists on [$credentials], skip store.")
                return
            }
            doStore(path, digest, artifactFile, credentials)
            logger.info("Success to store artifactFile [$digest] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to store artifactFile [$digest] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun store(digest: String, file: File, storageCredentials: StorageCredentials?) {
        val path = fileLocator.locate(digest)
        val credentials = getCredentialsOrDefault(storageCredentials)

        try {
            if (doExist(path, digest, credentials)) {
                logger.debug("File [$digest] exists on [$credentials], skip store.")
                return
            }
            doStore(path, digest, file, credentials)
            logger.info("Success to store file [$digest] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to store file [$digest] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun load(digest: String, storageCredentials: StorageCredentials?): File? {
        val path = fileLocator.locate(digest)
        val credentials = getCredentialsOrDefault(storageCredentials)

        try {
            return doLoad(path, digest, credentials)
        } catch (exception: Exception) {
            logger.error("Failed to load file [$digest] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun delete(digest: String, storageCredentials: StorageCredentials?) {
        val path = fileLocator.locate(digest)
        val credentials = getCredentialsOrDefault(storageCredentials)

        try {
            doDelete(path, digest, credentials)
            logger.info("Success to delete file [$digest] on [$credentials].")
        } catch (exception: Exception) {
            logger.error("Failed to delete file [$digest] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun exist(digest: String, storageCredentials: StorageCredentials?): Boolean {
        val path = fileLocator.locate(digest)
        val credentials = getCredentialsOrDefault(storageCredentials)

        try {
            return doExist(path, digest, credentials)
        } catch (exception: Exception) {
            logger.error("Failed to check file [$digest] exist on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun createAppendId(): String {
        try {
            val appendId = generateUniqueId()
            tempFileClient.touch(CURRENT_PATH, appendId)
            return appendId
        } catch (exception: Exception) {
            logger.error("Failed to create append id.", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun append(appendId: String, artifactFile: ArtifactFile): Long {
        try {
            val length = tempFileClient.append(CURRENT_PATH, appendId, artifactFile.getInputStream())
            logger.info("Success to append file [$appendId].")
            return length
        } catch (exception: Exception) {
            logger.error("Failed to append file [$appendId].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun finishAppend(appendId: String, storageCredentials: StorageCredentials?): FileInfo {
        val credentials = getCredentialsOrDefault(storageCredentials)
        try {
            tempFileClient.load(CURRENT_PATH, appendId)?.let {
                val fileInfo = storeFile(it, credentials)
                tempFileClient.delete(CURRENT_PATH, appendId)
                return fileInfo
            } ?: throw IllegalArgumentException("Append file does not exist.")
        } catch (exception: Exception) {
            logger.error("Failed to finish append file [$appendId] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun createBlockId(): String {
        try {
            val blockId = generateUniqueId()
            tempFileClient.createDirectory(CURRENT_PATH, blockId)
            return blockId
        } catch (exception: Exception) {
            logger.error("Failed to create block id.", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun checkBlockId(blockId: String): Boolean {
        try {
            return tempFileClient.checkDirectory(blockId)
        } catch (exception: Exception) {
            logger.error("Failed to check block id.", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun storeBlock(blockId: String, sequence: Int, digest: String, artifactFile: ArtifactFile) {
        try {
            tempFileClient.store(blockId, "$sequence$BLOCK_SUFFIX", artifactFile.getInputStream())
            tempFileClient.store(blockId, "$sequence$SHA256_SUFFIX", digest.byteInputStream())
            logger.debug("Success to store block [$blockId/$sequence].")
        } catch (exception: Exception) {
            logger.error("Failed to store block [$blockId/$sequence].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun mergeBlock(blockId: String, storageCredentials: StorageCredentials?): FileInfo {
        val credentials = getCredentialsOrDefault(storageCredentials)
        try {
            val blockFileList = tempFileClient.listFiles(blockId, BLOCK_EXTENSION)
                .sortedBy { it.name.removeSuffix(BLOCK_SUFFIX).toInt() }
            blockFileList.takeIf { it.isNotEmpty() } ?: throw StorageException(StorageMessageCode.BLOCK_EMPTY)
            for (index in blockFileList.indices) {
                val sequence = index + 1
                if (blockFileList[index].name.removeSuffix(BLOCK_SUFFIX).toInt() != sequence) {
                    throw StorageException(StorageMessageCode.BLOCK_MISSING, sequence.toString())
                }
            }
            val mergedFile = tempFileClient.mergeFiles(blockFileList, tempFileClient.touch(blockId, MERGED_FILENAME))
            val fileInfo = storeFile(mergedFile, credentials)
            tempFileClient.deleteDirectory(CURRENT_PATH, blockId)
            return fileInfo
        } catch (exception: Exception) {
            logger.error("Failed to combine block id [$blockId] on [$credentials].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun deleteBlockId(blockId: String) {
        try {
            tempFileClient.deleteDirectory(CURRENT_PATH, blockId)
            logger.info("Success to delete block id [$blockId].")
        } catch (exception: Exception) {
            logger.error("Failed to delete block id [$blockId].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun listBlock(blockId: String): List<Pair<Long, String>> {
        try {
            val blockFileList = tempFileClient.listFiles(blockId, BLOCK_EXTENSION)
                .sortedBy { it.name.removeSuffix(BLOCK_SUFFIX).toInt() }
            return blockFileList.map {
                val size = it.length()
                val digestFile = tempFileClient.load(blockId, it.name.replace(BLOCK_SUFFIX, SHA256_SUFFIX))
                val sha256 = FileUtils.readFileToString(digestFile, Charset.defaultCharset())
                Pair(size, sha256)
            }
        } catch (exception: Exception) {
            logger.error("Failed to list block [$blockId].", exception)
            throw StorageException(StorageMessageCode.STORE_ERROR, exception.message.toString())
        }
    }

    override fun cleanUp(): CleanupResult {
        return tempFileClient.cleanUp(storageProperties.cache.expireDays)
    }

    private fun storeFile(file: File, credentials: StorageCredentials): FileInfo {
        val digest = FileDigestUtils.fileSha256(listOf(file.inputStream()))
        val size = file.length()
        val fileInfo = FileInfo(digest, size)
        val path = fileLocator.locate(digest)
        if (!doExist(path, digest, credentials)) {
            doStore(path, digest, file, credentials)
        }
        return fileInfo
    }

    private fun determineTempPath(): String {
        return getTempPath() ?: fileStorage.getTempPath()
    }

    private fun generateUniqueId(): String {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase()
    }

    private fun getCredentialsOrDefault(storageCredentials: StorageCredentials?): StorageCredentials {
        return storageCredentials ?: fileStorage.getDefaultCredentials()
    }

    protected abstract fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials)
    protected abstract fun doStore(path: String, filename: String, file: File, credentials: StorageCredentials)
    protected abstract fun doLoad(path: String, filename: String, credentials: StorageCredentials): File?
    protected abstract fun doDelete(path: String, filename: String, credentials: StorageCredentials)
    protected abstract fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean
    open fun getTempPath(): String? = null

    companion object {
        private const val CURRENT_PATH = ""
        private const val BLOCK_SUFFIX = ".block"
        private const val BLOCK_EXTENSION = "block"
        private const val SHA256_SUFFIX = ".sha256"
        private const val MERGED_FILENAME = "merged.data"
        private val logger = LoggerFactory.getLogger(AbstractStorageService::class.java)
    }
}