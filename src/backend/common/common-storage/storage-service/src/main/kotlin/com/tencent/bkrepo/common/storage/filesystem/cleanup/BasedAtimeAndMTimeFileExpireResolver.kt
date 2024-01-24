package com.tencent.bkrepo.common.storage.filesystem.cleanup

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration

/**
 * 基于文件的atime和mtime判断文件是否过期
 * */
class BasedAtimeAndMTimeFileExpireResolver(
    /**
     * 过期时长
     * */
    private val expire: Duration,
) : FileExpireResolver {
    override fun isExpired(file: File): Boolean {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val lastAccessTime = attributes.lastAccessTime().toMillis()
        val lastModifiedTime = attributes.lastModifiedTime().toMillis()
        val expiredTime = System.currentTimeMillis() - expire.toMillis()
        return lastAccessTime < expiredTime && lastModifiedTime < expiredTime
    }
}
