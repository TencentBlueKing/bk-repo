/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.component.manager.arrowhead

import com.tencent.bkrepo.analyst.component.manager.ScannerConverter
import com.tencent.bkrepo.analyst.pojo.request.ArrowheadLoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.analyst.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.analyst.service.SpdxLicenseService
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.analysis.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ApplicationItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.CveSecItem
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.stereotype.Component
import java.util.Locale

@Component("${ArrowheadScanner.TYPE}Converter")
class ArrowheadConverter(private val licenseService: SpdxLicenseService) : ScannerConverter {

    @Suppress("UNCHECKED_CAST")
    override fun convertLicenseResult(result: Any): Page<FileLicensesResultDetail> {
        result as Page<ApplicationItem>
        // 查询数据库中存放的applicationItem时已经过滤了只存在license的项，license一定存在
        val licenseIds = result.records.map { it.license!!.name }.distinct()
        val licenses = licenseService.listLicenseByIds(licenseIds).mapKeys { it.key.lowercase(Locale.getDefault()) }

        val reports = result.records.map {
            val detail = licenses[it.license!!.name.lowercase(Locale.getDefault())]
            FileLicensesResultDetail(
                licenseId = it.license!!.name,
                fullName = detail?.name ?: "",
                compliance = detail?.isTrust,
                riskLevel = it.license!!.risk,
                recommended = detail?.isDeprecatedLicenseId == false,
                deprecated = detail?.isDeprecatedLicenseId == true,
                description = detail?.reference ?: "",
                isOsiApproved = detail?.isOsiApproved,
                dependentPath = it.path,
                isFsfLibre = detail?.isFsfLibre,
                pkgName = it.component
            )
        }.distinct()
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
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

    @Suppress("UNCHECKED_CAST")
    override fun convertCveResult(result: Any): Page<ArtifactVulnerabilityInfo> {
        result as Page<CveSecItem>
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        val reports = result.records.mapTo(HashSet(result.records.size)) {
            ArtifactVulnerabilityInfo(
                vulId = getVulId(it),
                severity = ScanPlanConverter.convertToLeakLevel(it.cvssRank),
                pkgName = it.component,
                installedVersion = it.versions,
                title = it.name,
                vulnerabilityName = it.name,
                description = it.description,
                officialSolution = it.officialSolution.ifEmpty { it.defenseSolution },
                reference = it.references,
                path = it.path
            )
        }.toList()
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
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
        scanExecutorResult as ArrowheadScanExecutorResult
        val overview = HashMap<String, Long>()
        scanExecutorResult.cveSecItems.forEach {
            val overviewKey = CveOverviewKey.overviewKeyOf(it.cvssRank)
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }
        addLicenseOverview(overview, scanExecutorResult.applicationItems)
        return overview
    }

    private fun addLicenseOverview(
        overview: MutableMap<String, Long>,
        applicationItems: List<ApplicationItem>
    ) {
        val licenseIds = HashSet<String>()
        val licenses = HashSet<ApplicationItem>()
        applicationItems.forEach { item ->
            item.license?.let {
                licenses.add(item)
                licenseIds.add(it.name)
            }
        }
        overview[LicenseOverviewKey.overviewKeyOf(LicenseOverviewKey.TOTAL)] = licenses.size.toLong()

        // 获取许可证详情
        val licenseInfo =
            licenseService.listLicenseByIds(licenseIds.toList()).mapKeys { it.key.lowercase(Locale.getDefault()) }
        for (license in licenses) {
            val detail = licenseInfo[license.license!!.name.lowercase(Locale.getDefault())]
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
    }

    private fun getVulId(cveSecItem: CveSecItem): String {
        with(cveSecItem) {
            if (cveId.isNotEmpty()) {
                return cveId
            }

            if (cnnvdId.isNotEmpty()) {
                return cnnvdId
            }

            if (cnvdId.isNotEmpty()) {
                return cnvdId
            }
            return pocId
        }
    }
}
