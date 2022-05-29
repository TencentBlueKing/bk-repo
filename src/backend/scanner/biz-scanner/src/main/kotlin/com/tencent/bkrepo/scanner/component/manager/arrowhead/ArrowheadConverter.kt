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

package com.tencent.bkrepo.scanner.component.manager.arrowhead

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.CveSecItem
import com.tencent.bkrepo.scanner.component.manager.ScannerConverter
import com.tencent.bkrepo.scanner.pojo.request.ArrowheadLoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.scanner.utils.ScanPlanConverter
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component("${ArrowheadScanner.TYPE}Converter")
class ArrowheadConverter : ScannerConverter {
    @Suppress("UNCHECKED_CAST")
    override fun convertCveResult(result: Any): Page<ArtifactVulnerabilityInfo> {
        result as Page<CveSecItem>
        val pageRequest = PageRequest.of(result.pageNumber, result.pageSize)
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
