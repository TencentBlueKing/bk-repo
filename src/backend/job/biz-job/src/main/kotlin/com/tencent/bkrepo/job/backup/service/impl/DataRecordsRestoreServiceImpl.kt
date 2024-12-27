package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.storage.util.createFile
import com.tencent.bkrepo.job.backup.config.DataBackupConfig
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PRIVATE_TYPE
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum.Companion.PUBLIC_TYPE
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.task.BackupContent
import com.tencent.bkrepo.job.backup.pojo.task.ProjectContentInfo
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
    private val dataBackupConfig: DataBackupConfig,
) : DataRecordsRestoreService, BaseService() {
    override fun projectDataRestore(context: BackupContext) {
        with(context) {
            logger.info("Start to run restore task ${context.task}.")
            startDate = LocalDateTime.now()
            backupTaskDao.updateState(taskId, BackupTaskState.RUNNING, startDate)
            // TODO 需要进行磁盘判断
            // TODO 异常需要捕获
            if (task.content == null) return
            preProcessFile(context)
            // 恢复公共基础数据
            if (task.content!!.commonData) {
                context.currentPath = context.targertPath
                commonDataRestore(context)
            }
            // 备份业务数据
            findSecondLevelDirectories(context.targertPath).forEach {
                if (filterFolder(it, task.content)) {
                    context.currentPath = it
                    customDataRestore(context)
                }
            }
            if (task.content!!.compression) {
                deleteFolder(targertPath)
            }
            backupTaskDao.updateState(taskId, BackupTaskState.FINISHED, endDate = LocalDateTime.now())
            logger.info("Restore task ${context.task} has been finished!")
        }
    }

    private fun preProcessFile(context: BackupContext) {
        with(context) {
            val path = Paths.get(context.task.storeLocation)
            if (!Files.exists(path)) {
                throw FileNotFoundException(context.task.storeLocation)
            }
            val targetFolder = if (task.content!!.compression) {
                freeSpaceCheck(context, dataBackupConfig.usageThreshold)
                decompressZipFile(path, context)
            } else {
                path
            }
            logger.info("restore root folder is $targetFolder")
            context.targertPath = targetFolder
        }
    }

    private fun decompressZipFile(sourcePath: Path, context: BackupContext): Path {
        val currentDateStr = context.startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))
        val tempFolder = sourcePath.name.removeSuffix(ZIP_FILE_SUFFIX) + StringPool.DASH + currentDateStr
        val unZipTempFolder = Paths.get(sourcePath.parent.toString(), tempFolder)
        unZipTempFolder.createFile()
        ZipFileUtil.decompressFile(context.task.storeLocation, unZipTempFolder.toString())
        if (!Files.exists(unZipTempFolder)) {
            throw FileNotFoundException(unZipTempFolder.toString())
        }
        val subdirectories = Files.list(unZipTempFolder)
            .filter { Files.isDirectory(it) }
            .toList()
        return subdirectories.firstOrNull() ?: throw FileNotFoundException(unZipTempFolder.toString())
    }

    fun findSecondLevelDirectories(directory: Path): List<Path> {
        val secondLevelDirectories = mutableListOf<Path>()

        Files.walk(directory, 2)
            .filter {
                Files.isDirectory(it) &&
                    it != directory &&
                    it.parent.name != BaseService.FILE_STORE_FOLDER
                    && it.parent != directory
            }
            .forEach { secondLevelDirectories.add(it) }

        return secondLevelDirectories
    }


    private fun filterFolder(path: Path, content: BackupContent?): Boolean {
        if (content == null) return false
        val currentFolderName = path.name
        val parentFolderName = path.parent.name
        content.projects?.forEach {
            if (projectFilter(it, parentFolderName) && repoFilter(it, currentFolderName)) return true
        }
        return false
    }

    private fun projectFilter(project: ProjectContentInfo, projectFolder: String): Boolean {
        var projectMatch = false
        if (project.projectId.isNullOrEmpty()) {
            if (project.projectRegex.isNullOrEmpty()) {
                projectMatch = project.excludeProjects?.contains(projectFolder) != true
            } else {
                val regex = Regex(project.projectRegex.replace("*", ".*"))
                projectMatch = regex.matches(projectFolder)
            }
        } else {
            projectMatch = projectFolder == project.projectId
        }
        return projectMatch
    }

    private fun repoFilter(project: ProjectContentInfo, repoFolder: String): Boolean {
        var repoMatch = false
        if (project.repoList.isNullOrEmpty()) {
            if (project.repoRegex.isNullOrEmpty()) {
                if (!project.excludeRepos.isNullOrEmpty()) {
                    repoMatch = project.excludeRepos.contains(repoFolder) != true
                } else {
                    repoMatch = true
                }
            } else {
                val regex = Regex(project.repoRegex.replace("*", ".*"))
                repoMatch = regex.matches(repoFolder)
            }
        } else {
            repoMatch = project.repoList.contains(repoFolder)
        }
        return repoMatch
    }

    private fun commonDataRestore(context: BackupContext) {
        BackupDataEnum.getNonSpecialDataList(PUBLIC_TYPE).forEach {
            logger.info("start to restore common data ${it.collectionName}!")
            try {
                processFiles(context, it)
            } catch (e: Exception) {
                logger.error("restore common data ${it.collectionName} error $e")
            }
            logger.info("common data ${it.collectionName} has been restored!")
        }
    }

    private fun customDataRestore(context: BackupContext) {
        BackupDataEnum.getNonSpecialDataList(PRIVATE_TYPE).forEach {
            logger.info("start to restore custom data ${it.collectionName} with folder ${context.currentPath}!")
            try {
                processFiles(context, it)
            } catch (e: Exception) {
                logger.error("restore custom data ${it.collectionName} with folder ${context.currentPath} error $e")
                throw e
            }
            logger.info("custom data ${it.collectionName} has been restored with folder ${context.currentPath}!")
        }
    }

    private fun processFiles(context: BackupContext, backupDataEnum: BackupDataEnum) {
        BackupDataMappings.preRestoreDataHandler(backupDataEnum, context)
        loadAndStoreRecord(backupDataEnum, context)
        val specialData = BackupDataMappings.getSpecialDataEnum(backupDataEnum, context) ?: return
        specialData.forEach {
            loadAndStoreRecord(it, context)
        }
    }

    private fun loadAndStoreRecord(backupDataEnum: BackupDataEnum, context: BackupContext) {
        with(context) {
            if (!Files.exists(Paths.get(currentFile))) {
                logger.warn("$currentFile not exist!")
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
