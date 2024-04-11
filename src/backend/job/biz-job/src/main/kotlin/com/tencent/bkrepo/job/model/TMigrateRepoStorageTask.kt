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

package com.tencent.bkrepo.job.model

import com.tencent.bkrepo.job.pojo.MigrateRepoStorageTaskState
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("migrate_repo_storage_task")
@CompoundIndexes(
    CompoundIndex(name = "projectId_repoName_idx", def = "{'projectId': 1, 'repoName': 1}", unique = true)
)
data class TMigrateRepoStorageTask(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,

    /**
     * 任务开始执行的时间
     */
    var startDate: LocalDateTime? = null,

    /**
     * 需要迁移的制品总数
     */
    val totalCount: Long? = null,

    /**
     * 已迁移的制品数
     */
    var migratedCount: Long = 0,

    /**
     * 迁移任务所属项目
     */
    var projectId: String,
    /**
     * 迁移项目所属仓库
     */
    var repoName: String,
    /**
     * 源存储，为null时表示默认存储
     */
    var srcStorageKey: String? = null,
    /**
     * 目标存储，为null时表示默认存储
     */
    var dstStorageKey: String? = null,
    /**
     * 迁移状态
     */
    var state: String = MigrateRepoStorageTaskState.PENDING.name
)
