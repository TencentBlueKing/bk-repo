package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PRIVATE_TYPE
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PUBLIC_TYPE
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.service.DataRecordsRestoreService
import com.tencent.bkrepo.job.backup.service.impl.base.BackupDataMappings
import com.tencent.bkrepo.job.backup.util.ZipFileUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.streams.toList

@Service
class DataRecordsRestoreServiceImpl(
    private val backupTaskDao: BackupTaskDao,
) : DataRecordsRestoreService, BaseService() {
    override fun projectDataRestore(context: BackupContext) {
        with(context) {
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            // TODO 需要进行磁盘判断
            if (task.content == null) return
            preProcessFile(context)
            // 恢复公共基础数据
            if (task.content!!.commonData) {
                commonDataRestore(context)
            }
            // 备份业务数据
            customDataRestore(context)
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
        }
    }

    private fun preProcessFile(context: BackupContext) {
        with(context) {
            val path = Paths.get(context.task.storeLocation)
            if (!Files.exists(path)) {
                throw FileNotFoundException(context.task.storeLocation)
            }
            val targetFolder = if (task.content!!.compression) {
                decompressZipFile(path, context)
            } else {
                path
            }
            context.targertPath = targetFolder
            context.tempClient = FileSystemClient(targetFolder)
        }
    }

    private fun decompressZipFile(sourcePath: Path, context: BackupContext): Path {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val tempFolder = sourcePath.name.removeSuffix(ZIP_FILE_SUFFIX) + StringPool.DASH + currentDateStr
        val unZipTempFolder = Paths.get(sourcePath.parent.toString(), tempFolder)
        ZipFileUtil.decompressFile(context.task.storeLocation, unZipTempFolder.toString())
        if (!Files.exists(unZipTempFolder)) {
            throw FileNotFoundException(unZipTempFolder.toString())
        }
        val subdirectories = Files.list(unZipTempFolder)
            .filter { Files.isDirectory(it) }
            .toList()
        return subdirectories.firstOrNull() ?: throw FileNotFoundException(unZipTempFolder.toString())
    }

    private fun commonDataRestore(context: BackupContext) {
        BackupDataEnum.getNonSpecialDataList(PUBLIC_TYPE).forEach {
            processFiles(context, it)
        }
    }

    private fun customDataRestore(context: BackupContext) {
        BackupDataEnum.getNonSpecialDataList(PRIVATE_TYPE).forEach {
            processFiles(context, it)
        }
    }

    private fun processFiles(context: BackupContext, backupDataEnum: BackupDataEnum) {
        context.currentFile = Paths.get(context.targertPath.toString(), backupDataEnum.fileName).toString()
        loadAndStoreRecord(backupDataEnum, context)
        val specialData = BackupDataMappings.getSpecialDataEnum(backupDataEnum, context) ?: return
        specialData.forEach {
            loadAndStoreRecord(it, context)
        }
    }

    private fun loadAndStoreRecord(backupDataEnum: BackupDataEnum, context: BackupContext) {
        with(context) {
            if (!Files.exists(Paths.get(currentFile))) {
                logger.error("$currentFile not exist!")
                return
            }
            val file = File(currentFile)
            file.forEachLine { line ->
                val record = JsonUtils.objectMapper.readValue(line, backupDataEnum.backupClazz)
                BackupDataMappings.storeRestoreDataHandler(record, backupDataEnum, context)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataRecordsRestoreServiceImpl::class.java)
    }
}