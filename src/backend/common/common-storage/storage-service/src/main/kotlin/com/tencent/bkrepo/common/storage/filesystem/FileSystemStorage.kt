package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import java.io.File
import java.io.InputStream
import java.nio.file.Paths

/**
 * 文件系统存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
open class FileSystemStorage : AbstractFileStorage<FileSystemCredentials, FileSystemClient>() {

    override fun store(path: String, filename: String, file: File, client: FileSystemClient) {
        client.store(path, filename, file)
    }

    override fun store(path: String, filename: String, inputStream: InputStream, client: FileSystemClient) {
        client.store(path, filename, inputStream, inputStream.available().toLong())
    }

    override fun load(path: String, filename: String, received: File, client: FileSystemClient): File? {
        return client.load(path, filename)?.run {
            FileLockExecutor.executeInLock(this.inputStream()) { input ->
                FileLockExecutor.executeInLock(received) { output ->
                    client.transfer(input, output, this.length())
                }
            }
            received
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

    override fun getDefaultCredentials() = storageProperties.filesystem
    override fun onCreateClient(credentials: FileSystemCredentials) = FileSystemClient(credentials.path)
    override fun getTempPath() = Paths.get(storageProperties.filesystem.path, TEMP).toString()

    companion object {
        const val TEMP = "temp"
    }
}
