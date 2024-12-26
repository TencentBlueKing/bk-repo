package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.common.storage.util.createFile
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.setting.BackupErrorStrategy
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


open class BaseService {


    fun freeSpaceCheck(context: BackupContext, usageThreshold: Double) {
        val currentDirectory = File(context.task.storeLocation)
        val freeSpace = currentDirectory.freeSpace
        val totalSpace = currentDirectory.totalSpace

        val freeSpacePercentage = (freeSpace.toDouble() / totalSpace.toDouble())
        val usedSpacePercentage = 1 - freeSpacePercentage
        if (usedSpacePercentage > usageThreshold) {
            val msg = "disk usage reached limit $usageThreshold"
            logger.error(msg)
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, msg)
        }
    }


    // TODO 全存储在一个文件中，当数据过多会导致内容过大
    fun <T> storeData(data: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val fileName = backupDataEnum.fileName
        try {
            val dir = if (context.currentProjectId.isNullOrEmpty()) {
                StringPool.EMPTY
            } else {
                context.currentProjectId + StringPool.SLASH + context.currentRepoName
            }
            val filePath = buildPath(dir, fileName, context.targertPath)
            touch(filePath)
            val dataStr = data!!.toJsonString().replace(System.lineSeparator(), "")
            appendToFile(dataStr, filePath.toString())
            logger.info("Success to append file [$fileName]")
        } catch (exception: Exception) {
            logger.error("Failed to create file", exception)
            if (context.task.backupSetting.errorStrategy == BackupErrorStrategy.FAST_FAIL) {
                throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            }
        }
    }

    fun deleteFolder(targetPath: Path) {
        try {
            //  最后需要删除目录
            deleteDirectory(targetPath)
        } catch (e: Exception) {
            logger.warn("delete temp folder error: ", e)
        }
    }

    fun buildPath(dir: String, fileName: String, root: Path): Path {
        return Paths.get(root.toString(), dir, fileName)
    }

    fun touch(filePath: Path): File {
        filePath.createFile()
        return filePath.toFile()
    }

    @Throws(IOException::class)
    fun appendToFile(content: String, filePath: String) {
        BufferedWriter(FileWriter(filePath, true)).use { writer ->
            writer.write(content)
            writer.newLine()
        }
    }

    @Throws(IOException::class)
    fun streamToFile(inputStream: InputStream, filePath: String) {
        inputStream.use { inputStream ->
            BufferedOutputStream(FileOutputStream(filePath)).use { outputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    fun exist(filePath: Path): Boolean {
        return Files.isRegularFile(filePath)
    }

    fun deleteDirectory(directory: Path) {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(BaseService::class.java)
        const val ZIP_FILE_SUFFIX = ".zip"
        const val FILE_STORE_FOLDER = "bk+store"
    }
}