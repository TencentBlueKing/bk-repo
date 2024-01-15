package com.tencent.bkrepo.common.storage.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class StorageUtils(
    private val fileStorage: FileStorage,
) {
    init {
        Companion.fileStorage = fileStorage
    }

    companion object {
        private lateinit var fileStorage: FileStorage
        private const val DOWNLOAD_PREFIX = "downloading_"
        private const val DOWNLOAD_SUFFIX = ".temp"

        /**
         * 下载文件到指定路径
         * @param path 文件源路径
         * @param digest 文件名
         * @param credentials 存储实例
         * @param filePath 下载目标路径
         * */
        fun download(
            path: String,
            digest: String,
            range: Range = Range.FULL_RANGE,
            credentials: StorageCredentials,
            filePath: Path,
        ) {
            val dir = credentials.upload.localPath
            val fileName = StringPool.randomStringByLongValue(DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX)
            val tempFile = FileSystemClient(dir).touch("", fileName)
            try {
                val inputStream = fileStorage.load(path, digest, range, credentials)
                    ?: error("Miss data $digest on ${credentials.key}")
                StreamUtils.useCopy(inputStream, tempFile.outputStream())
                Files.move(tempFile.toPath(), filePath)
            } finally {
                tempFile.delete()
            }
        }
    }
}
