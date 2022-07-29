package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseLevel
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.model.SubScanTaskDefinition
import com.tencent.bkrepo.scanner.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.pojo.ScanStatus
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.scanner.pojo.response.ScanLicensePlanInfo
import org.springframework.data.domain.PageRequest
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
            val scannerType = latestScanTask?.scannerType
            val overview = scanPlan.scanResultOverview

            var unCompliance = 0L
            var unRecommend = 0L
            var unknown = 0L
            var total = 0L

            unCompliance += getLicenseCount(scannerType, LicenseNature.UN_COMPLIANCE.natureName, overview)
            unRecommend += getLicenseCount(scannerType, LicenseNature.UN_RECOMMEND.natureName, overview)
            unknown += getLicenseCount(scannerType, LicenseNature.UNKNOWN.natureName, overview)
            total += getLicenseCount(scannerType, LICENSE_TOTAL, overview)

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
            val high = getLicenseCount(LicenseLevel.HIGH.levelName, subScanTask)
            val medium = getLicenseCount(LicenseLevel.MEDIUM.levelName, subScanTask)
            val low = getLicenseCount(LicenseLevel.LOW.levelName, subScanTask)
            val nil = getLicenseCount(LicenseLevel.NIL.levelName, subScanTask)
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

    @Suppress("UNCHECKED_CAST")
    fun convert(
        detailReport: Any?,
        scannerType: String,
        reportType: String,
        pageNumber: Int,
        pageSize: Int
    ): Page<FileLicensesResultDetail> {
        val pageRequest = PageRequest.of(pageNumber - 1, pageSize)
        if (scannerType == ScancodeToolkitScanner.TYPE && reportType == ScancodeItem.TYPE && detailReport != null) {
            detailReport as Page<ScancodeItem>
            val reports = detailReport.records.mapTo(HashSet(detailReport.records.size)) {
                FileLicensesResultDetail(
                    licenseId = it.licenseId,
                    fullName = it.fullName,
                    compliance = it.compliance,
                    riskLevel = it.riskLevel,
                    recommended = it.recommended,
                    description = it.description ?: "",
                    isOsiApproved = it.isOsiApproved,
                    dependentPath = it.dependentPath,
                    isFsfLibre = it.isFsfLibre
                )
            }.toList()
            return Pages.ofResponse(pageRequest, detailReport.totalRecords, reports)

        }
        return Pages.ofResponse(pageRequest, 0L, emptyList())
    }

    fun convertToLoadArguments(request: ArtifactLicensesDetailRequest, scannerType: String): LoadResultArguments? {
        return when (scannerType) {
            ScancodeToolkitScanner.TYPE -> {
                ScancodeToolkitResultArguments(
                    licenseIds = request.licenseId?.let { listOf(it) } ?: emptyList(),
                    riskLevels = request.riskLevel?.let { listOf(it) } ?: emptyList(),
                    reportType = request.reportType,
                    pageLimit = PageLimit(request.pageNumber, request.pageSize)
                )
            }
            else -> null
        }
    }


    private fun getLicenseCount(level: String, subtask: SubScanTaskDefinition): Long {
        return getLicenseCount(subtask.scannerType, level, subtask.scanResultOverview)
    }

    private fun getLicenseCount(scannerType: String?, level: String, overview: Map<String, Number>?): Long {
        if (scannerType == null) {
            return 0L
        }

        val key = getLicenseOverviewKey(scannerType, level)
        return overview?.get(key)?.toLong() ?: 0L
    }

    private fun getLicenseOverviewKey(scannerType: String, level: String): String {
        return when (scannerType) {
            ScancodeToolkitScanner.TYPE -> ScanCodeToolkitScanExecutorResult.overviewKeyOf(level)
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, scannerType, level)
        }
    }

    // 报告许可总数
    private const val LICENSE_TOTAL = "total"
}
