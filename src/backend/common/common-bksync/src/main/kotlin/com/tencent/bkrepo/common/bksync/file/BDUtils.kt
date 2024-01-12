package com.tencent.bkrepo.common.bksync.file

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils
import com.tencent.bkrepo.common.api.util.StreamUtils.drain
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource.Companion.toBkSyncDeltaSource
import com.tencent.bkrepo.common.bksync.transfer.exception.InterruptedRollingException
import com.tencent.bkrepo.common.bksync.transfer.exception.TooLowerReuseRateException
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import org.slf4j.LoggerFactory

/**
 * BD压缩工具
 *
 * 使用bksync实现的增量压缩和解压功能
 * */
object BDUtils {

    private const val BK_SYNC_FILE_PREFIX = "bksync_"
    private const val BK_SYNC_FILE_SUFFIX = ".temp"
    private val logger = LoggerFactory.getLogger(BDUtils::class.java)

    /**
     * 增量压缩
     *
     * 基于bksync实现增量压缩功能，压缩后生成bd格式文件
     * @param src 源文件
     * @param dest 目标文件
     * @param srcKey 源文件的key
     * @param destKey 目标文件的key
     * @param workDir 工作目录
     * @param threshold 源文件和目标文件最低重复率阈值
     * @return 压缩后的文件
     * @throws InterruptedRollingException 重复率低于[threshold]则抛出该异常
     * */
    fun delta(
        src: File,
        dest: File,
        srcKey: String,
        destKey: String,
        workDir: Path,
        threshold: Float,
    ): File {
        // 源md5
        val bkSync = BkSync()
        val path = workDir.resolve(BK_SYNC_FILE_PREFIX)
        try {
            // 签名文件
            val signFile = path.createTempFile()
            signFile.outputStream().use { bkSync.checksum(dest, it) }
            return deltaByChecksumFile(src, signFile, srcKey, destKey, workDir, threshold)
        } finally {
            path.toFile().deleteRecursively()
        }
    }

    /**
     * 增量压缩
     *
     * @param src 源文件
     * @param checksumFile 签名文件
     * @param srcKey 源文件的key
     * @param destKey 目标文件的key
     * @param workDir 工作目录
     * @param threshold 源文件和目标文件最低重复率阈值
     * @return 压缩后的文件
     * @throws InterruptedRollingException 重复率低于[threshold]则抛出该异常
     * */
    fun deltaByChecksumFile(
        src: File,
        checksumFile: File,
        srcKey: String,
        destKey: String,
        workDir: Path,
        threshold: Float,
    ): File {
        val bkSync = BkSync()
        val path = workDir.resolve(BK_SYNC_FILE_PREFIX)
        try {
            /*
            * 压缩过程
            * 1. 根据src和dest生成增量文件
            * 2. 计算原文件md5，并构建bd source
            * 3. 将bd source写入文件，生成bd文件
            * */
            val deltaFile = path.createTempFile()
            StreamUtils.use(checksumFile.inputStream(), deltaFile.outputStream()) { input, output ->
                val result = bkSync.diff(src, input, output, threshold)
                if (result.hitRate < threshold) {
                    logger.info("Repeat rate[${result.hitRate}] is below threshold[$threshold],stop detection.")
                    throw TooLowerReuseRateException()
                }
            }
            val messageDigest = MessageDigest.getInstance("MD5")
            DigestInputStream(src.inputStream(), messageDigest).use { it.drain() }
            val source = FileBkSyncDeltaSource(srcKey, destKey, messageDigest.digest(), deltaFile)
            val file = workDir.createTempFile()
            file.outputStream().use { source.writeTo(it) }
            return file
        } finally {
            path.toFile().deleteRecursively()
        }
    }

    /**
     * 解压文件
     * @param bdFile bd压缩文件
     * @param dest 目标文件
     * @param workDir 工作目录
     * @return 源文件
     * @throws IllegalStateException md5校验失败，则抛出该异常
     * */
    fun patch(bdFile: File, dest: File, workDir: Path): File {
        val path = workDir.resolve(BK_SYNC_FILE_PREFIX)
        val file = path.createTempFile()
        try {
            /*
            * 解压过程
            * 1. 读取bd文件
            * 2. 通过bksync合并delta和dest,合并生成源文件
            * 3. 校验源文件md5
            * */
            val bdSource = bdFile.toBkSyncDeltaSource(file)
            val srcFile = workDir.createTempFile()
            FileChannel.open(srcFile.toPath(), StandardOpenOption.WRITE).use { srcChannel ->
                bdSource.content().use { deltaInput ->
                    val bksync = BkSync()
                    bksync.calculateMd5 = true
                    val result = bksync.merge(dest, deltaInput, srcChannel)
                    check(result.md5 == bdSource.getSrcMd5()) { "File [${bdSource.src}] failed verification" }
                }
            }
            return srcFile
        } finally {
            path.toFile().deleteRecursively()
        }
    }

    /**
     * 创建临时文件
     * */
    private fun Path.createTempFile(): File {
        val srcFileName = StringPool.randomStringByLongValue(BK_SYNC_FILE_PREFIX, BK_SYNC_FILE_SUFFIX)
        val path = this.resolve(srcFileName)
        Files.createDirectories(path.parent)
        return Files.createFile(path).toFile()
    }
}
