/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.backup.model

import com.tencent.bkrepo.job.DATA_RECORDS_BACKUP
import com.tencent.bkrepo.job.backup.model.TBackupTask.Companion.TASK_STATE_IDX
import com.tencent.bkrepo.job.backup.model.TBackupTask.Companion.TASK_STATE_IDX_DEF
import com.tencent.bkrepo.job.backup.model.TBackupTask.Companion.TASK_TYPE_IDX
import com.tencent.bkrepo.job.backup.model.TBackupTask.Companion.TASK_TYPE_IDX_DEF
import com.tencent.bkrepo.job.backup.pojo.task.BackupContent
import com.tencent.bkrepo.job.backup.pojo.BackupTaskState
import com.tencent.bkrepo.job.backup.pojo.setting.BackupSetting
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 备份与恢复任务
 */
@Document("backup_task")
@CompoundIndexes(
    CompoundIndex(name = TASK_STATE_IDX, def = TASK_STATE_IDX_DEF, background = true),
    CompoundIndex(name = TASK_TYPE_IDX, def = TASK_TYPE_IDX_DEF, background = true),
)
data class TBackupTask(
    val id: String? = null,
    val name: String,
    val storeLocation: String,
    val type: String = DATA_RECORDS_BACKUP,
    var content: BackupContent? = null,
    val backupSetting: BackupSetting,
    val state: String = BackupTaskState.PENDING.name,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
) {
    companion object {
        const val TASK_STATE_IDX = "state_idx"
        const val TASK_STATE_IDX_DEF = "{'state': 1}"
        const val TASK_TYPE_IDX = "type_idx"
        const val TASK_TYPE_IDX_DEF = "{'type': 1}"
    }
}
