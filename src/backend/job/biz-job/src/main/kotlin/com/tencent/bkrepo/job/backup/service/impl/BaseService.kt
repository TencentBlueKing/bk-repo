package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import org.slf4j.LoggerFactory

open class BaseService {

    // TODO 全存储在一个文件中，当数据过多会导致内容过大
    fun <T> storeData(data: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val fileName = backupDataEnum.fileName
        try {
            val dir = if (context.currentProjectId.isNullOrEmpty()) {
                StringPool.EMPTY
            } else {
                context.currentProjectId + StringPool.SLASH + context.currentRepoName
            }
            context.tempClient.touch(dir, fileName)
            logger.info("Success to create file [$fileName]")
            val dataStr = data!!.toJsonString().replace(System.lineSeparator(), "")
            val inputStream = dataStr.byteInputStream()
            val size = dataStr.length.toLong()
            context.tempClient.append(StringPool.EMPTY, fileName, inputStream, size)
            val lineEndStr = "\n"
            context.tempClient.append(
                StringPool.EMPTY,
                fileName,
                lineEndStr.byteInputStream(),
                lineEndStr.length.toLong()
            )
            logger.info("Success to append file [$fileName]")
        } catch (exception: Exception) {
            logger.error("Failed to create file", exception)
            throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            // TODO 异常该如何处理
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(BaseService::class.java)
        const val ZIP_FILE_SUFFIX = ".zip"
    }
}