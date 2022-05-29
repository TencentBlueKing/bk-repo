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

package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.scanner.model.TProjectScanConfiguration
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import java.time.format.DateTimeFormatter

object Converter {
    fun convert(subScanTask: TSubScanTask, scanner: Scanner): SubScanTask = with(subScanTask) {
        SubScanTask(
            taskId = id!!,
            parentScanTaskId = parentScanTaskId,
            scanner = scanner,
            sha256 = sha256,
            size = size,
            credentialsKey = credentialsKey
        )
    }

    fun convert(scanTask: TScanTask, scanPlan: TScanPlan? = null, force: Boolean = false): ScanTask = with(scanTask) {
        ScanTask(
            taskId = id!!,
            createdBy = createdBy,
            lastModifiedDateTime = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
            triggerDateTime = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
            startDateTime = startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            finishedDateTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            status = status,
            scanPlan = scanPlan?.let { ScanPlanConverter.convert(it) },
            rule = scanTask.rule?.readJsonString(),
            total = total,
            scanning = scanning,
            failed = failed,
            scanned = scanned,
            scanner = scanner,
            scannerType = scannerType,
            scannerVersion = scannerVersion,
            scanResultOverview = scanResultOverview,
            force = force
        )
    }

    fun convert(projectScanConfiguration: TProjectScanConfiguration): ProjectScanConfiguration {
        return with(projectScanConfiguration) {
            ProjectScanConfiguration(
                projectId = projectId,
                priority = priority,
                scanTaskCountLimit = scanTaskCountLimit,
                subScanTaskCountLimit = subScanTaskCountLimit,
                autoScanConfiguration = autoScanConfiguration
            )
        }
    }

    fun convert(overview: Map<String, Any?>): Map<String, Number> {
        val numberOverview = HashMap<String, Number>(overview.size)
        overview.forEach {
            if (it.value is Number) {
                numberOverview[it.key] = it.value as Number
            }
        }
        return numberOverview
    }
}
