package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.job.DATA_RECORDS_BACKUP
import com.tencent.bkrepo.job.DATA_RECORDS_RESTORE
import com.tencent.bkrepo.job.backup.dao.BackupTaskDao
import com.tencent.bkrepo.job.backup.model.TBackupTask
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.pojo.task.BackupTask
import com.tencent.bkrepo.job.backup.pojo.task.BackupTask.Companion.toDto
import com.tencent.bkrepo.job.backup.pojo.task.BackupTaskRequest
import com.tencent.bkrepo.job.backup.service.DataBackupService
import com.tencent.bkrepo.job.backup.service.DataRecordsBackupService
import com.tencent.bkrepo.job.backup.service.DataRecordsRestoreService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.time.LocalDateTime

@Service
class DataBackupServiceImpl(
    private val backupTaskDao: BackupTaskDao,
    private val dataRecordsBackupService: DataRecordsBackupService,
    private val dataRecordsRestoreService: DataRecordsRestoreService
) : DataBackupService {
    override fun createTask(taskRequest: BackupTaskRequest): String {
        contentCheck(taskRequest)
        val task = buildBackupTask(taskRequest)
        return backupTaskDao.save(task).id!!
    }

    override fun executeTask(taskId: String) {
        val records = backupTaskDao.findTasksById(taskId)
        val task = records.firstOrNull() ?: throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "taskId")
        if (task.state != BackupTaskState.PENDING.name)
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "state")
        if (task.type == DATA_RECORDS_BACKUP) {
            val context = BackupContext(task = task)
            dataRecordsBackupService.projectDataBackup(context)
        } else {
            val context = BackupContext(task = task)
            dataRecordsRestoreService.projectDataRestore(context)
        }
    }

    override fun findTasks(state: String?, pageRequest: PageRequest): Page<BackupTask> {
        val count = backupTaskDao.count(state)
        val records = backupTaskDao.find(state, pageRequest).map { it.toDto() }
        return Pages.ofResponse(pageRequest, count, records)
    }

    private fun contentCheck(request: BackupTaskRequest) {
        with(request) {
            try {
                val targetFile = Paths.get(storeLocation).toFile()
                if (!targetFile.exists()) throw FileNotFoundException(storeLocation)
            } catch (e: Exception) {
                logger.warn("backup store location [$storeLocation] is illegal!")
                throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, BackupTaskRequest::storeLocation.name)
            }
            when (type) {
                DATA_RECORDS_BACKUP -> {
                    if (content == null || content!!.projects.isNullOrEmpty()) {
                        logger.warn("backup content [$content] is illegal!")
                        throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, BackupTaskRequest::content.name)
                    }
                    return
                }
                DATA_RECORDS_RESTORE -> {
                    return
                }
                else -> {
                    logger.warn("task type [$type] is illegal!")
                    throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, BackupTaskRequest::type.name)
                }
            }
        }
    }

    private fun buildBackupTask(request: BackupTaskRequest): TBackupTask {
        return TBackupTask(
            name = request.name,
            storeLocation = request.storeLocation,
            type = request.type,
            content = request.content,
            createdBy = SecurityUtils.getUserId(),
            createdDate = LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
            lastModifiedBy = SecurityUtils.getUserId(),
            backupSetting = request.backupSetting
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataBackupServiceImpl::class.java)
    }
}