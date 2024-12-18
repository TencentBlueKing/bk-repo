package com.tencent.bkrepo.job.backup.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.job.backup.pojo.task.BackupTask
import com.tencent.bkrepo.job.backup.pojo.task.BackupTaskRequest
import org.springframework.data.domain.PageRequest

interface DataBackupService {

    fun createTask(taskRequest: BackupTaskRequest): String

    fun executeTask(taskId: String)

    fun findTasks(state: String?, pageRequest: PageRequest): Page<BackupTask>
}