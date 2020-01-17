package com.tencent.bkrepo.common.storage.filesystem.cleanup

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
class CleanupFileVisitor(
    private val rootPath: Path,
    private val expireDays: Int
) : SimpleFileVisitor<Path>() {

    val cleanupResult = CleanupResult()

    @Throws(IOException::class)
    override fun visitFile(filePath: Path, attributes: BasicFileAttributes): FileVisitResult {
        if (isExpired(attributes, expireDays)) {
            val size = attributes.size()
            Files.deleteIfExists(filePath)
            cleanupResult.fileCount += 1
            cleanupResult.size += size
        }
        return FileVisitResult.CONTINUE
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dirPath: Path, exc: IOException?): FileVisitResult {
        if (!Files.isSameFile(rootPath, dirPath) && !Files.list(dirPath).iterator().hasNext()) {
            Files.deleteIfExists(dirPath)
            cleanupResult.folderCount += 1
        }
        return FileVisitResult.CONTINUE
    }

    /**
     * 判断文件是否过期
     * 根据上次访问时间和上次修改时间判断
     */
    private fun isExpired(attributes: BasicFileAttributes, expireDays: Int): Boolean {
        val lastAccessTime = attributes.lastAccessTime().toMillis()
        val lastModifiedTime = attributes.lastModifiedTime().toMillis()
        val expiredTime = System.currentTimeMillis() - expireDays * 24 * 3600 * 1000
        return lastAccessTime < expiredTime && lastModifiedTime < expiredTime
    }
}
