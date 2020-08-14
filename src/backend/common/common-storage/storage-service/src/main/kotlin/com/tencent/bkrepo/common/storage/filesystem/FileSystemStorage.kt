package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.api.constant.StringPool.TEMP
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import java.io.File
import java.io.InputStream
import java.nio.file.Paths

/**
 * 文件系统存储
 */
open class FileSystemStorage : AbstractFileStorage<FileSystemCredentials, FileSystemClient>() {

    override fun store(path: String, filename: String, file: File, client: FileSystemClient) {
        file.inputStream().use {
            client.store(path, filename, it, file.length())
        }
    }

    override fun store(path: String, filename: String, inputStream: InputStream, size: Long, client: FileSystemClient) {
        inputStream.use {
            client.store(path, filename, it, size)
        }
    }

    override fun load(path: String, filename: String, range: Range, client: FileSystemClient): InputStream? {
        return client.load(path, filename)?.bound(range)
    }

    override fun delete(path: String, filename: String, client: FileSystemClient) {
        client.delete(path, filename)
    }

    override fun exist(path: String, filename: String, client: FileSystemClient): Boolean {
        return client.exist(path, filename)
    }

    override fun onCreateClient(credentials: FileSystemCredentials) = FileSystemClient(credentials.path)

    override fun getTempPath(storageCredentials: StorageCredentials): String {
        storageCredentials as FileSystemCredentials
        return Paths.get(storageCredentials.path, TEMP).toString()
    }
}
