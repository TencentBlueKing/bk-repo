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

@file:Suppress("DEPRECATION")

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey.NIL
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey.TOTAL
import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.pojo.ScanStatus
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.analyst.pojo.response.ScanLicensePlanInfo
import java.time.format.DateTimeFormatter

object ScanLicenseConverter {

    fun convert(scanPlan: TScanPlan, subScanTasks: List<TPlanArtifactLatestSubScanTask>): ScanLicensePlanInfo {
        with(scanPlan) {
            var unCompliance = 0L
            var unRecommend = 0L
            var unknown = 0L
            var total = 0L

            subScanTasks.forEach { subScanTask ->
                unCompliance += getLicenseCount(LicenseNature.UN_COMPLIANCE.natureName, subScanTask)
                unRecommend += getLicenseCount(LicenseNature.UN_RECOMMEND.natureName, subScanTask)
                unknown += getLicenseCount(LicenseNature.UNKNOWN.natureName, subScanTask)
                total += getLicenseCount(TOTAL, subScanTask)
            }
            return ScanLicensePlanInfo(
                id = id!!,
                name = name,
                planType = type,
                projectId = projectId,
                status = "",
                artifactCount = subScanTasks.size.toLong(),
                unCompliance = unCompliance,
                unRecommend = unRecommend,
                unknown = unknown,
                total = total,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastScanDate = null
            )
        }
    }

    fun convert(scanPlan: TScanPlan, latestScanTask: TScanTask?, artifactCount: Long): ScanLicensePlanInfo {
        with(scanPlan) {
            val overview = scanPlan.scanResultOverview

            var unCompliance = 0L
            var unRecommend = 0L
            var unknown = 0L
            var total = 0L

            unCompliance += getLicenseCount(LicenseNature.UN_COMPLIANCE.natureName, overview)
            unRecommend += getLicenseCount(LicenseNature.UN_RECOMMEND.natureName, overview)
            unknown += getLicenseCount(LicenseNature.UNKNOWN.natureName, overview)
            total += getLicenseCount(TOTAL, overview)

            val status =
                latestScanTask?.let { ScanPlanConverter.convertToScanStatus(it.status).name } ?: ScanStatus.INIT.name

            return ScanLicensePlanInfo(
                id = id!!,
                name = name,
                planType = type,
                projectId = projectId,
                status = status,
                artifactCount = artifactCount,
                unCompliance = unCompliance,
                unRecommend = unRecommend,
                unknown = unknown,
                total = total,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastScanDate = latestScanTask?.startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    fun convert(subScanTask: SubScanTaskDefinition): FileLicensesResultOverview {
        return with(subScanTask) {
            val high = getLicenseCount(Level.HIGH.levelName, subScanTask)
            val medium = getLicenseCount(Level.MEDIUM.levelName, subScanTask)
            val low = getLicenseCount(Level.LOW.levelName, subScanTask)
            val nil = getLicenseCount(NIL, subScanTask)
            val total = getLicenseCount(TOTAL, subScanTask)

            FileLicensesResultOverview(
                subTaskId = subScanTask.id!!,
                name = artifactName,
                packageKey = packageKey,
                version = version,
                fullPath = fullPath,
                repoType = repoType,
                repoName = repoName,
                high = high,
                medium = medium,
                low = low,
                nil = nil,
                total = total,
                finishTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                qualityRedLine = qualityRedLine,
                scanQuality = scanQuality,
                duration = ScanPlanConverter.duration(startDateTime, finishedDateTime),
                scanStatus = ScanPlanConverter.convertToScanStatus(status, qualityRedLine).name
            )
        }
    }

    fun getLicenseCount(level: String, subtask: SubScanTaskDefinition): Long {
        return getLicenseCount(level, subtask.scanResultOverview)
    }

    private fun getLicenseCount(level: String, overview: Map<String, Number>?): Long {
        val key = LicenseOverviewKey.overviewKeyOf(level)
        return overview?.get(key)?.toLong() ?: 0L
    }
}
