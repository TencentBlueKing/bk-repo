/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.pojo

import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import io.swagger.v3.oas.annotations.media.Schema

import java.time.LocalDateTime

@Schema(title = "数据迁移任务")
data class MigrateRepoStorageTask(
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
    @get:Schema(title = "需要迁移的制品总数")
    val totalCount: Long? = null,
    @get:Schema(title = "已迁移的制品数")
    val migratedCount: Long = 0,
    @get:Schema(title = "已迁移的最后一个Node ID")
    val lastMigratedNodeId: String = MIN_OBJECT_ID,
    @get:Schema(title = "迁移任务所属项目")
    val projectId: String,
    @get:Schema(title = "迁移项目所属仓库")
    val repoName: String,
    @get:Schema(title = "源存储，为null时表示默认存储")
    val srcStorageKey: String? = null,
    @get:Schema(title = "目标存储，为null时表示默认存储")
    val dstStorageKey: String? = null,
    @get:Schema(title = "迁移状态")
    val state: String,
    @get:Schema(title = "当前正在执行该任务的实例instanceId")
    val executingOn: String? = null,
) {
    companion object {
        fun TMigrateRepoStorageTask.toDto() = MigrateRepoStorageTask(
            id = id,
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = lastModifiedBy,
            lastModifiedDate = lastModifiedDate,
            startDate = startDate,
            totalCount = totalCount,
            migratedCount = migratedCount,
            lastMigratedNodeId = lastMigratedNodeId,
            projectId = projectId,
            repoName = repoName,
            srcStorageKey = srcStorageKey,
            dstStorageKey = dstStorageKey,
            state = state,
            executingOn = executingOn,
        )
    }
}
