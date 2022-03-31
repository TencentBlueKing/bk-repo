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

package com.tencent.bkrepo.scanner.model

import com.tencent.bkrepo.scanner.utils.Converter
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 已完成扫描的子任务
 */
@Document("finished_sub_scan_task")
class TFinishedSubScanTask(
    id: String? = null,
    createdBy: String,
    createdDate: LocalDateTime,
    lastModifiedBy: String,
    lastModifiedDate: LocalDateTime,
    startDateTime: LocalDateTime?,
    finishedDateTime: LocalDateTime,

    parentScanTaskId: String,
    planId: String?,

    projectId: String,
    repoName: String,
    repoType: String,
    packageKey: String? = null,
    version: String? = null,
    fullPath: String,
    artifactName: String,

    status: String,
    executedTimes: Int,
    scanner: String,
    scannerType: String,
    sha256: String,
    size: Long,
    credentialsKey: String?,

    scanResultOverview: Map<String, Number>?
) : SubScanTaskDefinition(
    id = id,
    createdBy = createdBy,
    createdDate = createdDate,
    lastModifiedBy = lastModifiedBy,
    lastModifiedDate = lastModifiedDate,
    startDateTime = startDateTime,
    finishedDateTime = finishedDateTime,
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
    credentialsKey = credentialsKey,
    scanResultOverview = scanResultOverview
) {
    companion object {
        fun from(
            task: TSubScanTask,
            resultStatus: String,
            overview: Map<String, Any?>,
            modifiedBy: String? = null,
            now: LocalDateTime = LocalDateTime.now()
        ) = with(task) {
            val numberOverview = Converter.convert(overview)
            TFinishedSubScanTask(
                id = id,
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = modifiedBy ?: lastModifiedBy,
                lastModifiedDate = now,
                startDateTime = startDateTime,
                finishedDateTime = now,
                parentScanTaskId = parentScanTaskId,
                planId = planId,
                projectId = projectId,
                repoName = repoName,
                repoType = repoType,
                packageKey = packageKey,
                version = version,
                fullPath = fullPath,
                artifactName = artifactName,
                status = resultStatus,
                executedTimes = executedTimes,
                scanner = scanner,
                scannerType = scannerType,
                sha256 = sha256,
                size = size,
                credentialsKey = credentialsKey,
                scanResultOverview = numberOverview
            )
        }
    }
}
