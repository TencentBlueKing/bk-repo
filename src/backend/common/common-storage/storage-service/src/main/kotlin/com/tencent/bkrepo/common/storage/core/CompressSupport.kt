package com.tencent.bkrepo.common.storage.core

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import com.tencent.bkrepo.common.api.util.StreamUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource
import com.tencent.bkrepo.common.bksync.file.BDUtils
import com.tencent.bkrepo.common.bksync.transfer.exception.TooLowerReuseRateException
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.common.storage.util.createFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import org.slf4j.LoggerFactory

/**
 * 压缩操作实现类
 * */
abstract class CompressSupport : OverlaySupport() {

    /**
     * 签名文件缓存
     * */
    private val checksumFileCache: LoadingCache<FileKey, File> by lazy {
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(12))
            .maximumSize(1000)
            .removalListener(
                RemovalListener<FileKey, File> {
                    it.value?.delete()
                    logger.info("Delete checksum file ${it.value?.absolutePath}")
                },
            )
            .build(CacheLoader.from(this::signFile))
    }

    override fun compress(
        digest: String,
        base: String,
        storageCredentials: StorageCredentials?,
        keep: Boolean,
    ): Long {
        // 增量存储源文件和基础文件必须不同，不然会导致base文件丢失
        require(digest != base) { "Incremental storage source file and base file must be different." }
        val credentials = getCredentialsOrDefault(storageCredentials)
        val workDir = getWorkDir(digest, credentials)

        // 文件正在被压缩
        if (Files.exists(workDir)) {
            logger.info("The file [$digest] is compressing.")
            return -1
        }

        // 文件已经被压缩
        if (isCompressed(digest, storageCredentials)) {
            val bdFileName = digest.plus(BD_FILE_SUFFIX)
            val bdFilePath = fileLocator.locate(bdFileName)
            fileStorage.load(bdFilePath, bdFileName, Range.FULL_RANGE, credentials)?.use {
                return BkSyncDeltaSource.readHeader(it).size
            }
        }
        // 压缩文件
        try {
            /*
            * 压缩过程
            * 1. 下载源文件
            * 2. 获取base签名文件
            * 3. 根据原文件和签名文件，进行BD压缩，产生bd压缩文件
            * 4. 存储bd压缩文件
            * 5. 删除原文件（可选，根据keep参数决定）
            * */
            val originFile = download(digest, credentials, workDir)
            val baseFileKey = FileKey(base, credentials)
            // base file可能会被多个其他版本文件使用，所以这里对基文件的签名进行了缓存
            var checksumFile = checksumFileCache.get(baseFileKey)
            if (!checksumFile.exists()) {
                checksumFileCache.invalidate(baseFileKey)
                checksumFile = checksumFileCache.get(baseFileKey)
            }
            val threshold = credentials.compress.ratio
            val bdFile = BDUtils.deltaByChecksumFile(originFile, checksumFile, digest, base, workDir, threshold)
            val newFileName = digest.plus(BD_FILE_SUFFIX)
            val newFilePath = fileLocator.locate(newFileName)
            fileStorage.store(newFilePath, newFileName, bdFile, credentials)
            if (!keep) {
                delete(digest, credentials)
            }
            logger.info("Success to compress file [$digest] on ${credentials.key}.")
            return bdFile.length()
        } catch (e: TooLowerReuseRateException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to compress file [$digest] on ${credentials.key}.", e)
            throw StorageErrorException(StorageMessageCode.COMPRESS_ERROR)
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    override fun uncompress(digest: String, storageCredentials: StorageCredentials?): Int {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val path = fileLocator.locate(digest)
        val workDir = getWorkDir(digest, credentials)
        // 文件正在被解压或者不存在压缩文件，直接返回
        if (Files.exists(workDir) || !isCompressed(digest, storageCredentials)) {
            logger.info("The file[$digest] does not exist or is being decompressed.")
            return 0
        }
        // 已有未解压的文件，删除压缩文件
        if (fileStorage.exist(path, digest, credentials)) {
            deleteCompressed(digest, storageCredentials)
            return 1
        }
        // 解压文件
        try {
            /*
            * 解压过程
            * 1. 下载digest的bd压缩文件
            * 2. 读取base信息，并判断base是否也是压缩文件，如果是先解压base
            * 3. 下载base原文件
            * 4. 根据bd压缩文件和base原文件，合并成digest原文件
            * 5. 存储digest原文件
            * 6. 删除压缩文件
            * */
            val bdFileName = digest.plus(BD_FILE_SUFFIX)
            val bdFile = download(bdFileName, credentials, workDir)
            val base = bdFile.inputStream().use { BkSyncDeltaSource.readHeader(it).dest }
            if (isCompressed(base, credentials)) {
                logger.info("Base file [$base] is a delta file too,restore it first.")
                uncompress(base, credentials)
            }
            val baseFile = download(base, credentials, workDir)
            val originFile = BDUtils.patch(bdFile, baseFile, workDir)
            fileStorage.store(path, digest, originFile, credentials)
            delete(bdFileName, storageCredentials)
            logger.info("Success to restore $digest on ${credentials.key}")
            return 1
        } catch (e: Exception) {
            logger.error("Failed to restore file [$digest] on ${credentials.key}.", e)
            throw StorageErrorException(StorageMessageCode.RESTORE_ERROR)
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    override fun isCompressed(digest: String, storageCredentials: StorageCredentials?): Boolean {
        val bdFileName = digest.plus(BD_FILE_SUFFIX)
        return exist(bdFileName, storageCredentials)
    }

    override fun deleteCompressed(digest: String, storageCredentials: StorageCredentials?) {
        val bdFileName = digest.plus(BD_FILE_SUFFIX)
        delete(bdFileName, storageCredentials)
    }

    /**
     * 获取压缩工作路径
     * */
    private fun getWorkDir(digest: String, storageCredentials: StorageCredentials): Path {
        return Paths.get(storageCredentials.compress.path, COMPRESS_WORK_DIR, digest)
    }

    /**
     * 下载[digest]到指定目录[dir]
     * */
    protected fun download(digest: String, credentials: StorageCredentials, dir: Path): File {
        val tempFile = FileSystemClient(dir).touch("", "$digest.temp")
        try {
            val path = fileLocator.locate(digest)
            val inputStream = fileStorage.load(path, digest, Range.FULL_RANGE, credentials)
                ?: error("Miss data $digest on ${credentials.key}")
            StreamUtils.useCopy(inputStream, tempFile.outputStream())
            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * 签名指定的文件
     * */
    private fun signFile(key: FileKey): File {
        with(key) {
            logger.info("Start sign file [$digest].")
            val signDir = Paths.get(storageCredentials.compress.path, SIGN_WORK_DIR)
            val file = download(digest, storageCredentials, signDir)
            try {
                val checksumFile = signDir.resolve(digest.plus(SIGN_FILE_SUFFIX)).createFile()
                checksumFile.outputStream().use { BkSync().checksum(file, it) }
                logger.info("Success to generate sign file [${checksumFile.absolutePath}].")
                return checksumFile
            } finally {
                file.delete()
                logger.info("Delete temp file ${file.absolutePath}.")
            }
        }
    }

    private data class FileKey(
        val digest: String,
        val storageCredentials: StorageCredentials,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(CompressSupport::class.java)
        private const val COMPRESS_WORK_DIR = "compress"
        private const val SIGN_WORK_DIR = "sign"
        private const val BD_FILE_SUFFIX = ".bd"
        private const val SIGN_FILE_SUFFIX = ".checksum"
    }
}
