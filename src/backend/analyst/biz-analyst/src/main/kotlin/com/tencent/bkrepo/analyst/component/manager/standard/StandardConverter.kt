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

package com.tencent.bkrepo.analyst.component.manager.standard

import com.tencent.bkrepo.analyst.component.manager.ScannerConverter
import com.tencent.bkrepo.analyst.component.manager.ScannerConverter.Companion.OVERVIEW_KEY_SENSITIVE_TOTAL
import com.tencent.bkrepo.analyst.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.analyst.pojo.request.standard.StandardLoadResultArguments
import com.tencent.bkrepo.analyst.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.analyst.service.SpdxLicenseService
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey.TOTAL
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanType
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.LicenseResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SecurityResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SensitiveResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.stereotype.Component

@Component("${StandardScanner.TYPE}Converter")
class StandardConverter(private val licenseService: SpdxLicenseService) : ScannerConverter {
    @Suppress("UNCHECKED_CAST")
    override fun convertLicenseResult(result: Any): Page<FileLicensesResultDetail> {
        result as Page<LicenseResult>
        val licenseIds = result.records.map { it.licenseName }.distinct()
        val licenses = licenseService.listLicenseByIds(licenseIds).mapKeys { it.key.toLowerCase() }

        val reports = result.records.map {
            val detail = licenses[it.licenseName.toLowerCase()]
            FileLicensesResultDetail(
                licenseId = it.licenseName,
                fullName = detail?.name ?: "",
                compliance = detail?.isTrust,
                riskLevel = null,
                recommended = detail?.isDeprecatedLicenseId == false,
                deprecated = detail?.isDeprecatedLicenseId == true,
                description = detail?.reference ?: "",
                isOsiApproved = detail?.isOsiApproved,
                dependentPath = it.path ?: "",
                isFsfLibre = detail?.isFsfLibre,
                pkgName = it.pkgName
            )
        }
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    @Suppress("UNCHECKED_CAST")
    override fun convertCveResult(result: Any): Page<ArtifactVulnerabilityInfo> {
        result as Page<SecurityResult>
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        val reports = result.records.mapTo(LinkedHashSet(result.records.size)) {
            ArtifactVulnerabilityInfo(
                vulId = it.vulId,
                cveId = it.cveId,
                severity = ScanPlanConverter.convertToLeakLevel(it.severity),
                pkgName = it.pkgName ?: "",
                installedVersion = it.pkgVersions,
                title = it.vulName ?: "",
                vulnerabilityName = it.vulName ?: "",
                description = it.des,
                officialSolution = it.solution ?: "",
                reference = it.references,
                path = it.path,
                versionsPaths = it.versionsPaths
            )
        }.toList()
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    override fun convertToLoadArguments(request: ArtifactLicensesDetailRequest): LoadResultArguments {
        return StandardLoadResultArguments(
            licenseIds = request.licenseId?.let { listOf(it) } ?: emptyList(),
            reportType = ScanType.LICENSE.name,
            ignored = request.ignored,
            pageLimit = PageLimit(request.pageNumber, request.pageSize)
        )
    }

    override fun convertToLoadArguments(request: ArtifactVulnerabilityRequest): LoadResultArguments {
        return StandardLoadResultArguments(
            vulnerabilityLevels = request.leakType?.let { listOf(it) } ?: emptyList(),
            vulIds = request.vulId?.let { listOf(it) } ?: emptyList(),
            reportType = ScanType.SECURITY.name,
            pageLimit = PageLimit(request.pageNumber, request.pageSize),
            ignored = request.ignored
        )
    }

    override fun convertOverview(scanExecutorResult: ScanExecutorResult): Map<String, Any?> {
        val result = (scanExecutorResult as StandardScanExecutorResult).output?.result
        return convertOverview(result?.securityResults, result?.sensitiveResults, result?.licenseResults)
    }

    fun convertOverview(
        securityResults: List<SecurityResult>?,
        sensitiveResults: List<SensitiveResult>?,
        licenseResults: List<LicenseResult>?,
        filterRule: MergedFilterRule? = null
    ): Map<String, Any?> {
        val overview = HashMap<String, Long>()

        // security统计
        securityResults?.forEach { securityResult ->
            val severityLevel = Level.valueOf(securityResult.severity.toUpperCase()).level
            val shouldIgnore = filterRule?.shouldIgnore(
                securityResult.vulId,
                securityResult.cveId,
                securityResult.pkgName,
                securityResult.pkgVersions,
                severityLevel
            )
            if (shouldIgnore != true) {
                val key = CveOverviewKey.overviewKeyOf(securityResult.severity)
                overview[key] = overview.getOrDefault(key, 0L) + 1
            }
        }

        // sensitive统计
        sensitiveResults?.let {
            overview[OVERVIEW_KEY_SENSITIVE_TOTAL] = it.size.toLong()
        }

        // license统计
        if (licenseResults.isNullOrEmpty()) {
            return overview
        }

        val licenseIds = licenseResults.map { it.licenseName.toLowerCase() }.distinct()
        val licensesInfo = licenseService.listLicenseByIds(licenseIds).mapKeys { it.key.toLowerCase() }

        overview[LicenseOverviewKey.overviewKeyOf(TOTAL)] = licenseResults.size.toLong()
        for (licenseResult in licenseResults) {
            if (filterRule?.shouldIgnore(licenseResult.licenseName) == true) {
                val total = overview[LicenseOverviewKey.overviewKeyOf(TOTAL)] as Long
                overview[LicenseOverviewKey.overviewKeyOf(TOTAL)] = total - 1
                continue
            }

            val detail = licensesInfo[licenseResult.licenseName.toLowerCase()]
            if (detail == null) {
                incLicenseOverview(overview, LicenseNature.UNKNOWN.natureName)
                continue
            }

            if (detail.isDeprecatedLicenseId) {
                incLicenseOverview(overview, LicenseNature.UN_RECOMMEND.natureName)
            }

            if (!detail.isTrust) {
                incLicenseOverview(overview, LicenseNature.UN_COMPLIANCE.natureName)
            }
        }
        return overview
    }
}
