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

package com.tencent.bkrepo.analyst.component.manager.trivy

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanner
import com.tencent.bkrepo.analyst.component.manager.ScannerConverter
import com.tencent.bkrepo.analyst.component.manager.trivy.model.TVulnerabilityItem
import com.tencent.bkrepo.analyst.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.trivy.TrivyLoadResultArguments
import com.tencent.bkrepo.analyst.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.util.Locale

@Component("${TrivyScanner.TYPE}Converter")
class TrivyConverter : ScannerConverter {
    @Suppress("UNCHECKED_CAST")
    override fun convertCveResult(result: Any): Page<ArtifactVulnerabilityInfo> {
        result as Page<TVulnerabilityItem>
        val pageRequest = PageRequest.of(result.pageNumber, result.pageSize)
        val reports = result.records.mapTo(HashSet(result.records.size)) {
            ArtifactVulnerabilityInfo(
                vulId = it.data.vulnerabilityId,
                severity = ScanPlanConverter.convertToLeakLevel(it.data.severity.lowercase(Locale.getDefault())),
                pkgName = it.data.pkgName,
                installedVersion = setOf(it.data.installedVersion),
                title = it.data.title,
                vulnerabilityName = "",
                description = it.data.description,
                officialSolution = "",
                reference = it.data.references,
                path = ""
            )
        }.toList()
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    override fun convertToLoadArguments(request: ArtifactVulnerabilityRequest): LoadResultArguments {
        return TrivyLoadResultArguments(
            vulnerabilityLevels = request.leakType?.let { listOf(it) } ?: emptyList(),
            vulIds = request.vulId?.let { listOf(it) } ?: emptyList(),
            pageLimit = PageLimit(request.pageNumber, request.pageSize)
        )
    }

    override fun convertOverview(scanExecutorResult: ScanExecutorResult): Map<String, Any?> {
        scanExecutorResult as TrivyScanExecutorResult
        val overview = HashMap<String, Long>()
        // cve count
        scanExecutorResult.vulnerabilityItems.forEach {
            if (it.severity == "UNKNOWN") {
                it.severity = Level.CRITICAL.levelName.uppercase(Locale.getDefault())
            }
            val overviewKey = CveOverviewKey.overviewKeyOf(it.severity.lowercase(Locale.getDefault()))
            overview[overviewKey] = overview.getOrDefault(overviewKey, 0L) + 1L
        }
        return overview
    }
}
