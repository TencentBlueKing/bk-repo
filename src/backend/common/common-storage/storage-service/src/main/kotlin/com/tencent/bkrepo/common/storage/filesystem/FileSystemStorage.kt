package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import java.io.File
import java.nio.file.Paths

/**
 * 文件系统存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
open class FileSystemStorage : AbstractFileStorage<FileSystemCredentials, FileSystemClient>() {

    override fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        getClient(storageCredentials).store(path, filename, file.inputStream(), true)
    }

    override fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File? {
        return getClient(storageCredentials).load(path, filename)
    }

    override fun delete(path: String, filename: String, storageCredentials: StorageCredentials) {
        getClient(storageCredentials).delete(path, filename)
    }

    override fun exist(path: String, filename: String, storageCredentials: StorageCredentials): Boolean {
        return getClient(storageCredentials).exist(path, filename)
    }

    override fun onCreateClient(credentials: FileSystemCredentials) = FileSystemClient(credentials.path)

    override fun getDefaultCredentials() = storageProperties.filesystem

    override fun getTempPath() = Paths.get(storageProperties.filesystem.path, "temp").toString()
}
