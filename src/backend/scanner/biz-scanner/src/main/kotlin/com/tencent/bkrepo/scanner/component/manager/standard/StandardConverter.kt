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

package com.tencent.bkrepo.scanner.component.manager.standard

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey.TOTAL
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ApplicationItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.LicenseResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.SecurityResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.scanner.component.manager.ScannerConverter
import com.tencent.bkrepo.scanner.pojo.request.ArrowheadLoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.scanner.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.scanner.service.SpdxLicenseService
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
                isFsfLibre = detail?.isFsfLibre
            )
        }
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    @Suppress("UNCHECKED_CAST")
    override fun convertCveResult(result: Any): Page<ArtifactVulnerabilityInfo> {
        result as Page<SecurityResult>
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        val reports = result.records.mapTo(HashSet(result.records.size)) {
            ArtifactVulnerabilityInfo(
                vulId = it.vulId,
                severity = it.severity,
                pkgName = it.pkgName ?: "",
                installedVersion = it.pkgVersions,
                title = it.vulName ?: "",
                vulnerabilityName = it.vulName ?: "",
                description = it.des,
                officialSolution = it.solution ?: "",
                reference = it.references,
                path = it.path
            )
        }.toList()
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    override fun convertToLoadArguments(request: ArtifactLicensesDetailRequest): LoadResultArguments {
        return ArrowheadLoadResultArguments(
            licenseIds = request.licenseId?.let { listOf(it) } ?: emptyList(),
            riskLevels = request.riskLevel?.let { listOf(it) } ?: emptyList(),
            reportType = ApplicationItem.TYPE,
            pageLimit = PageLimit(request.pageNumber, request.pageSize)
        )
    }

    override fun convertToLoadArguments(request: ArtifactVulnerabilityRequest): LoadResultArguments {
        return ArrowheadLoadResultArguments(
            vulnerabilityLevels = request.leakType?.let { listOf(it) } ?: emptyList(),
            vulIds = request.vulId?.let { listOf(it) } ?: emptyList(),
            reportType = request.reportType,
            pageLimit = PageLimit(request.pageNumber, request.pageSize)
        )
    }

    override fun convertOverview(scanExecutorResult: ScanExecutorResult): Map<String, Any?> {
        scanExecutorResult as StandardScanExecutorResult
        val overview = HashMap<String, Long>()

        // security统计
        scanExecutorResult.output?.result?.securityResults?.forEach { securityResult ->
            val key = CveOverviewKey.overviewKeyOf(securityResult.severity)
            overview[key] = overview.getOrDefault(key, 0L) + 1
        }

        // license统计
        val licenseResults = scanExecutorResult.output?.result?.licenseResults
        if (licenseResults.isNullOrEmpty()) {
            return overview
        }

        val licenseIds = licenseResults.map { it.licenseName.toLowerCase() }.distinct()
        val licensesInfo = licenseService.listLicenseByIds(licenseIds)

        overview[LicenseOverviewKey.overviewKeyOf(TOTAL)] = licenseResults.size.toLong()
        for (licenseResult in licenseResults) {
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

    fun incLicenseOverview(overview: MutableMap<String, Long>, level: String) {
        val overviewKey = LicenseOverviewKey.overviewKeyOf(level)
        overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
    }
}
