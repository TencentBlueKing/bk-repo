/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import java.time.LocalDateTime

object SubtaskConverter {
    fun convertToSubtask(task: TArchiveSubScanTask, metadata: List<TaskMetadata>) = with(task) {
        TSubScanTask(
            id = id,
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = createdBy,
            lastModifiedDate = lastModifiedDate,
            startDateTime = startDateTime,

            triggerType = triggerType,
            parentScanTaskId = parentScanTaskId,
            planId = planId,

            projectId = projectId,
            repoName = repoName,
            repoType = repoType,
            packageKey = packageKey,
            version = version,
            fullPath = fullPath,
            artifactName = artifactName,

            status = status,
            executedTimes = executedTimes,
            scanner = scanner,
            scannerType = scannerType,
            sha256 = sha256,
            size = size,
            packageSize = packageSize,
            credentialsKey = credentialsKey,
            scanQuality = scanQuality,
            metadata = metadata
        )
    }

    fun convertToArchiveSubtask(
        task: TSubScanTask,
        status: String,
        overview: Map<String, Any?>? = null,
        modifiedBy: String? = null,
        qualityPass: Boolean? = null
    ) = with(task) {
        val now = LocalDateTime.now()
        val numberOverview = overview?.let { Converter.convert(it) }
        val finishedDateTime = if (SubScanTaskStatus.finishedStatus(status)) {
            now
        } else {
            null
        }
        TArchiveSubScanTask(
            id = id,
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = modifiedBy ?: lastModifiedBy,
            lastModifiedDate = now,
            startDateTime = startDateTime,
            finishedDateTime = finishedDateTime,
            triggerType = triggerType,
            parentScanTaskId = parentScanTaskId,
            planId = planId,
            projectId = projectId,
            repoName = repoName,
            repoType = repoType,
            packageKey = packageKey,
            version = version,
            fullPath = fullPath,
            artifactName = artifactName,
            status = status,
            executedTimes = executedTimes,
            scanner = scanner,
            scannerType = scannerType,
            sha256 = sha256,
            size = size,
            packageSize = packageSize,
            credentialsKey = credentialsKey,
            scanResultOverview = numberOverview,
            qualityRedLine = qualityPass,
            scanQuality = scanQuality
        )
    }

    fun convertToPlanSubtask(
        task: SubScanTaskDefinition,
        resultStatus: String,
        overview: Map<String, Any?>? = null,
        modifiedBy: String? = null,
        qualityPass: Boolean? = null
    ) = with(task) {
        val now = LocalDateTime.now()
        val numberOverview = overview?.let { Converter.convert(it) } ?: task.scanResultOverview
        val finishedDateTime = if (SubScanTaskStatus.finishedStatus(resultStatus)) {
            now
        } else {
            null
        }
        TPlanArtifactLatestSubScanTask(
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = modifiedBy ?: lastModifiedBy,
            lastModifiedDate = now,
            startDateTime = startDateTime,
            finishedDateTime = finishedDateTime,
            triggerType = triggerType,
            parentScanTaskId = parentScanTaskId,
            latestSubScanTaskId = id,
            planId = planId,
            projectId = projectId,
            repoName = repoName,
            repoType = repoType,
            packageKey = packageKey,
            version = version,
            fullPath = fullPath,
            artifactName = artifactName,
            status = resultStatus,
            scanner = scanner,
            scannerType = scannerType,
            sha256 = sha256,
            size = size,
            packageSize = packageSize,
            credentialsKey = credentialsKey,
            scanResultOverview = numberOverview,
            qualityRedLine = qualityPass ?: qualityRedLine,
            scanQuality = scanQuality
        )
    }

    fun convert(
        subScanTask: SubScanTaskDefinition,
        scanner: Scanner
    ): SubScanTask = with(subScanTask) {
        val extra = if (subScanTask is TSubScanTask) {
            subScanTask.metadata.associate { Pair(it.key, it.value) }
        } else {
            null
        }
        val taskId = if (subScanTask is TPlanArtifactLatestSubScanTask) {
            subScanTask.latestSubScanTaskId!!
        } else {
            subScanTask.id!!
        }
        SubScanTask(
            taskId = taskId,
            parentScanTaskId = parentScanTaskId,
            scanner = scanner,
            projectId = projectId,
            repoName = repoName,
            repoType = repoType,
            packageKey = packageKey,
            version = version,
            fullPath = fullPath,
            sha256 = sha256,
            size = size,
            packageSize = packageSize,
            credentialsKey = credentialsKey,
            createdBy = createdBy,
            extra = extra
        )
    }
}
