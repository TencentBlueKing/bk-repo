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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TProjectScanConfiguration
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.response.SubtaskInfo
import com.tencent.bkrepo.analyst.pojo.response.SubtaskResultOverview
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.util.readJsonString
import java.time.format.DateTimeFormatter

object Converter {
    fun convert(
        subScanTask: TSubScanTask,
        scanner: Scanner
    ): SubScanTask = with(subScanTask) {
        SubScanTask(
            taskId = id!!,
            parentScanTaskId = parentScanTaskId,
            scanner = scanner,
            projectId = projectId,
            repoName = repoName,
            repoType = repoType,
            fullPath = fullPath,
            sha256 = sha256,
            size = size,
            packageSize = packageSize,
            credentialsKey = credentialsKey,
            extra = metadata.associate { Pair(it.key, it.value) }
        )
    }

    fun convert(
        scanTask: TScanTask,
        scanPlan: TScanPlan? = null,
        force: Boolean = false
    ): ScanTask = with(scanTask) {
        ScanTask(
            name = scanTask.name,
            taskId = id!!,
            projectId = projectId,
            createdBy = createdBy,
            lastModifiedDateTime = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
            triggerDateTime = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
            startDateTime = startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            finishedDateTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            triggerType = triggerType,
            status = status,
            scanPlan = scanPlan?.let { ScanPlanConverter.convert(it) },
            rule = scanTask.rule?.readJsonString(),
            total = total,
            scanning = scanning,
            failed = failed,
            scanned = scanned,
            passed = passed,
            scanner = scanner,
            scannerType = scannerType,
            scannerVersion = scannerVersion,
            scanResultOverview = scanResultOverview,
            force = force,
            metadata = metadata
        )
    }

    fun convert(projectScanConfiguration: TProjectScanConfiguration): ProjectScanConfiguration {
        return with(projectScanConfiguration) {
            ProjectScanConfiguration(
                projectId = projectId,
                priority = priority,
                scanTaskCountLimit = scanTaskCountLimit,
                subScanTaskCountLimit = subScanTaskCountLimit,
                autoScanConfiguration = autoScanConfiguration,
                dispatcherConfiguration = dispatcherConfiguration
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

    fun convertToSubtaskInfo(subScanTask: SubScanTaskDefinition): SubtaskInfo {
        return with(subScanTask) {
            SubtaskInfo(
                recordId = id!!,
                subTaskId = id!!,
                name = artifactName,
                packageKey = packageKey,
                version = version,
                fullPath = fullPath,
                repoType = repoType,
                repoName = repoName,
                highestLeakLevel = scanResultOverview?.let { highestLeakLevel(it) },
                duration = ScanPlanConverter.duration(startDateTime, finishedDateTime),
                finishTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                status = ScanPlanConverter.convertToScanStatus(status).name,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                qualityRedLine = qualityRedLine
            )
        }
    }

    fun convert(subScanTask: SubScanTaskDefinition): SubtaskResultOverview {
        return with(subScanTask) {
            val critical = getCveCount(Level.CRITICAL.levelName, subScanTask)
            val high = getCveCount(Level.HIGH.levelName, subScanTask)
            val medium = getCveCount(Level.MEDIUM.levelName, subScanTask)
            val low = getCveCount(Level.LOW.levelName, subScanTask)

            SubtaskResultOverview(
                recordId = subScanTask.id!!,
                subTaskId = subScanTask.id!!,
                scanner = scanner,
                scannerType = scannerType,
                name = artifactName,
                packageKey = packageKey,
                version = version,
                fullPath = fullPath,
                repoType = repoType,
                repoName = repoName,
                highestLeakLevel = scanResultOverview?.let { highestLeakLevel(it) },
                critical = critical,
                high = high,
                medium = medium,
                low = low,
                total = critical + high + medium + low,
                finishTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                qualityRedLine = qualityRedLine,
                scanQuality = scanQuality,
                duration = ScanPlanConverter.duration(startDateTime, finishedDateTime)
            )
        }
    }

    fun getCveCount(level: String, subtask: SubScanTaskDefinition): Long {
        return getCveCount(subtask.scannerType, level, subtask.scanResultOverview)
    }

    fun getCveCount(scannerType: String?, level: String, overview: Map<String, Number>?): Long {
        if (scannerType == null) {
            return 0L
        }

        val key = getCveOverviewKey(level)
        return overview?.get(key)?.toLong() ?: 0L
    }

    fun getCveOverviewKey(level: String): String {
        return CveOverviewKey.overviewKeyOf(level)
    }

    private fun highestLeakLevel(overview: Map<String, Number>): String? {
        Level.values().forEach {
            if (overview.keys.contains(getCveOverviewKey(it.levelName))) {
                return ScanPlanConverter.convertToLeakLevel(it.levelName)
            }
        }
        return null
    }
}
