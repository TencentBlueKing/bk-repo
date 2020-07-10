package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import java.io.File
import java.io.InputStream

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
interface FileStorage {
    @Retryable(Exception::class, label = "FileStorage.store", maxAttempts = 1, backoff = Backoff(delay = 60 * 1000, multiplier = 2.0))
    fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials)
    fun store(path: String, filename: String, inputStream: InputStream, size: Long, storageCredentials: StorageCredentials)
    fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File?
    fun load(path: String, filename: String, range: Range, storageCredentials: StorageCredentials): InputStream?
    fun delete(path: String, filename: String, storageCredentials: StorageCredentials)
    fun exist(path: String, filename: String, storageCredentials: StorageCredentials): Boolean
    fun getTempPath(storageCredentials: StorageCredentials): String = System.getProperty("java.io.tmpdir")
    @Recover
    fun recover(exception: Exception, path: String, filename: String, file: File, storageCredentials: StorageCredentials)
}
