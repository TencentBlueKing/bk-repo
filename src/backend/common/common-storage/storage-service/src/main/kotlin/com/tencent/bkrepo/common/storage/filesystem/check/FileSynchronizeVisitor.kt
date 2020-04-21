package com.tencent.bkrepo.common.storage.filesystem.check

import com.tencent.bkrepo.common.api.constant.JOB_LOGGER_NAME
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class FileSynchronizeVisitor(
    private val tempPath: Path,
    private val fileLocator: FileLocator,
    private val fileStorage: FileStorage,
    private val credential: StorageCredentials
) : SimpleFileVisitor<Path>() {

    val checkResult = SynchronizeResult()

    @Throws(IOException::class)
    override fun visitFile(filePath: Path, attributes: BasicFileAttributes): FileVisitResult {
        try {
            if (!checkExist(filePath)) {
                val size = upload(filePath)
                checkResult.totalSize += size
                checkResult.synchronizedCount += 1
            } else {
                checkResult.ignoredCount += 1
            }
        } catch (exception: Exception) {
            logger.error("Synchronize file[${filePath.fileName}] error.", exception)
            checkResult.errorCount += 1
        } finally {
            checkResult.totalCount += 1
        }
        return FileVisitResult.CONTINUE
    }

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
        return if (dir.compareTo(tempPath) == 0) {
            FileVisitResult.SKIP_SUBTREE
        } else {
            FileVisitResult.CONTINUE
        }
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dirPath: Path, exc: IOException?): FileVisitResult {
        return FileVisitResult.CONTINUE
    }

    private fun checkExist(filePath: Path): Boolean {
        val filename = filePath.fileName.toString()
        val path = fileLocator.locate(filename)
        return fileStorage.exist(path, filename, credential)
    }

    private fun upload(filePath: Path): Long {
        val filename = filePath.fileName.toString()
        val path = fileLocator.locate(filename)
        val file = filePath.toFile()
        logger.info("Synchronize file[$filename]")
        fileStorage.synchronizeStore(path, filename, file, credential)
        return file.length()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JOB_LOGGER_NAME)
    }
}
