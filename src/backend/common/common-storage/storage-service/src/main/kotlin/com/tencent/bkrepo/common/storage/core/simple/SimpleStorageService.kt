package com.tencent.bkrepo.common.storage.core.simple

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.apache.commons.lang.RandomStringUtils
import java.io.File

/**
 * 存储服务简单实现
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
class SimpleStorageService : AbstractStorageService() {

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        // force to write file
        val file = if (artifactFile.isInMemory()) {
            val tempArtifactFile = ArtifactFileFactory.build(0)
            artifactFile.getInputStream().use { input ->
                tempArtifactFile.getOutputStream().use { output -> ByteStreams.copy(input, output) }
            }
            tempArtifactFile.getTempFile()
        } else {
            artifactFile.getTempFile()
        }
        fileStorage.synchronizeStore(path, filename, file, credentials)
    }

    override fun doStore(path: String, filename: String, file: File, credentials: StorageCredentials) {
        fileStorage.synchronizeStore(path, filename, file, credentials)
    }

    override fun doLoad(path: String, filename: String, credentials: StorageCredentials): File? {
        val tempArtifactFile = ArtifactFileFactory.build(0)
        return fileStorage.load(path, filename, tempArtifactFile.getTempFile(), credentials) ?: run {
            tempArtifactFile.delete()
            null
        }
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        fileStorage.delete(path, filename, credentials)
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.exist(path, filename, credentials)
    }

    override fun doManualRetry(path: String, filename: String, credentials: StorageCredentials) {
        throw ErrorCodeException(CommonMessageCode.OPERATION_UNSUPPORTED)
    }

    override fun doCheckHealth(credentials: StorageCredentials) {
        val path = "health-check"
        val filename = System.nanoTime().toString()
        val randomSize = 10

        val content = RandomStringUtils.randomAlphabetic(randomSize)
        var writeFile: File? = null
        var receiveFile: File? = null
        try {
            writeFile = File.createTempFile(path, filename)
            receiveFile = File.createTempFile(path, filename)
            writeFile.writeText(content)
            // 写文件
            fileStorage.synchronizeStore(path, filename, receiveFile, credentials)
            // 读文件
            fileStorage.load(path, filename, receiveFile, credentials)
            assert(receiveFile != null) { "Failed to load file." }
            assert(content == receiveFile!!.readText()) {"File content inconsistent."}
            // 删除文件
            fileStorage.delete(path, filename, credentials)
        } catch (exception: Exception) {
            throw exception
        } finally {
            writeFile?.deleteOnExit()
            receiveFile?.deleteOnExit()
        }
    }
}
