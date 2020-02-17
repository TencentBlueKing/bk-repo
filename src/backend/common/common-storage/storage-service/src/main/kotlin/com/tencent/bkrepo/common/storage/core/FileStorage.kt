package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import java.io.File

/**
 * 文件存储接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
interface FileStorage {
    @Async
    @Retryable(Exception::class, label = "FileStorage.store", maxAttempts = 5, backoff = Backoff(delay = 60 * 1000, multiplier = 2.0))
    fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials)
    fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File?
    fun delete(path: String, filename: String, storageCredentials: StorageCredentials)
    fun exist(path: String, filename: String, storageCredentials: StorageCredentials): Boolean

    fun getDefaultCredentials(): StorageCredentials
    fun getTempPath(): String = System.getProperty("java.io.tmpdir")

    @Recover
    fun recover(exception: Exception, path: String, filename: String, file: File, storageCredentials: StorageCredentials)
}
