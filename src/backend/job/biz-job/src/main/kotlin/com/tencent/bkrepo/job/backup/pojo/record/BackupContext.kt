/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.backup.pojo.record

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.backup.model.TBackupTask
import com.tencent.bkrepo.job.backup.pojo.query.BackupNodeInfo
import java.nio.file.Path
import java.time.LocalDateTime

class BackupContext(
    val task: TBackupTask,
) {
    lateinit var targertPath: Path
    lateinit var startDate: LocalDateTime
    var currentPath: Path? = null
    var type: String = task.type
    var taskId: String = task.id!!
    var currentProjectId: String? = null
    var currentRepoName: String? = null
    var currentRepositoryType: RepositoryType? = null
    var currentStorageCredentials: StorageCredentials? = null
    var currentPackageId: String? = null
    var currentPackageKey: String? = null
    var currentVersionName: String? = null
    var currentNode: BackupNodeInfo? = null
    var currentFile: String? = null
    var incrementDate: LocalDateTime? = null
}