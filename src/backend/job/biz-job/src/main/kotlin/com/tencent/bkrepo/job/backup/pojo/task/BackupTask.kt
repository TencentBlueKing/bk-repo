/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.backup.pojo.task

import com.tencent.bkrepo.job.backup.model.TBackupTask
import com.tencent.bkrepo.job.backup.pojo.setting.BackupSetting
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "数据备份任务")
data class BackupTask(
    @get:Schema(title = "ID")
    val id: String? = null,
    @get:Schema(title = "创建人")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @get:Schema(title = "最后修改人")
    val lastModifiedBy: String,
    @get:Schema(title = "最后修改时间")
    val lastModifiedDate: LocalDateTime,
    @get:Schema(title = "任务开始执行的时间")
    val startDate: LocalDateTime? = null,
    @get:Schema(title = "任务结束执行的时间")
    val endDate: LocalDateTime? = null,
    @get:Schema(title = "任务状态")
    val state: String,
    @get:Schema(title = "任务内容")
    val content: BackupContent?,
    @get:Schema(title = "存储路径")
    val storeLocation: String,
    @get:Schema(title = "任务配置")
    val backupSetting: BackupSetting,
    @get:Schema(title = "任务类型")
    val type: String,
) {
    companion object {
        fun TBackupTask.toDto() = BackupTask(
            id = id,
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = lastModifiedBy,
            lastModifiedDate = lastModifiedDate,
            startDate = startDate,
            endDate = endDate,
            state = state,
            content = content,
            storeLocation = storeLocation,
            backupSetting = backupSetting,
            type = type
        )
    }
}
