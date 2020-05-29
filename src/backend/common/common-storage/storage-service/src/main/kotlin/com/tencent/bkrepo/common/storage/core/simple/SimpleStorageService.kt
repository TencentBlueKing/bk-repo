package com.tencent.bkrepo.common.storage.core.simple

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.apache.commons.lang.RandomStringUtils
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 存储服务简单实现
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
class SimpleStorageService : AbstractStorageService() {

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        if (artifactFile.isInMemory()) {
            fileStorage.store(path, filename, artifactFile.getInputStream(), credentials)
        } else {
            fileStorage.store(path, filename, artifactFile.getFile()!!, credentials)
        }
    }

    override fun doLoad(path: String, filename: String, range: Range, credentials: StorageCredentials): InputStream? {
        return fileStorage.load(path, filename, range, credentials)
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
        val filename = System.nanoTime().toString()
        val size = 100

        val content = RandomStringUtils.randomAlphabetic(size)
        try {
            // write
            fileStorage.store(HEALTH_CHECK_PATH, filename, content.byteInputStream(), credentials)
            // read
            val loadedContent = fileStorage.load(HEALTH_CHECK_PATH, filename, Range.ofFull(size.toLong()), credentials)
                ?.readBytes()?.toString(Charset.defaultCharset()).orEmpty()
            // check
            assert(content == loadedContent) { "File content inconsistent." }
        } catch (exception: Exception) {
            throw exception
        } finally {
            // delete
            fileStorage.delete(HEALTH_CHECK_PATH, filename, credentials)
        }
    }
}
