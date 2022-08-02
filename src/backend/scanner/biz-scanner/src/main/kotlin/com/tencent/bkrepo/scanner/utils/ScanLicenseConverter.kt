package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.scanner.pojo.scanner.Level
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.scanner.model.SubScanTaskDefinition
import com.tencent.bkrepo.scanner.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.pojo.ScanStatus
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.scanner.pojo.response.ScanLicensePlanInfo
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
                total += getLicenseCount(LICENSE_TOTAL, subScanTask)
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
            total += getLicenseCount(LICENSE_TOTAL, overview)

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
            val nil = getLicenseCount(LICENSE_NIL, subScanTask)
            val total = getLicenseCount(LICENSE_TOTAL, subScanTask)

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
                duration = ScanPlanConverter.duration(startDateTime, finishedDateTime)
            )
        }
    }

    private fun getLicenseCount(level: String, subtask: SubScanTaskDefinition): Long {
        return getLicenseCount(level, subtask.scanResultOverview)
    }

    private fun getLicenseCount(level: String, overview: Map<String, Number>?): Long {
        val key = LicenseOverviewKey.overviewKeyOf(level)
        return overview?.get(key)?.toLong() ?: 0L
    }

    // 报告许可总数
    private const val LICENSE_TOTAL = "total"
    private const val LICENSE_NIL = "nil"
}
