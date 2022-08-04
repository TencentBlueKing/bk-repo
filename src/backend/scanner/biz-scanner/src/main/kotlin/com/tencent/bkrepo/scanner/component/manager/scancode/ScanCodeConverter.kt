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

package com.tencent.bkrepo.scanner.component.manager.scancode

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.scanner.component.manager.ScannerConverter
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.scanner.service.SpdxLicenseService
import org.springframework.stereotype.Component

@Component("${ScancodeToolkitScanner.TYPE}Converter")
class ScanCodeConverter(
    private val licenseService: SpdxLicenseService
) : ScannerConverter {
    @Suppress("UNCHECKED_CAST")
    override fun convertLicenseResult(result: Any): Page<FileLicensesResultDetail> {
        result as Page<ScancodeItem>
        val licenseIds = result.records.map { it.licenseId }.distinct()
        val licenses = licenseService.listLicenseByIds(licenseIds)

        val reports = result.records.mapTo(HashSet(result.records.size)) {
            val detail = licenses[it.licenseId]
            FileLicensesResultDetail(
                licenseId = it.licenseId,
                fullName = detail?.name ?: "",
                compliance = detail?.isTrust,
                riskLevel = it.riskLevel,
                recommended = detail?.isDeprecatedLicenseId == false,
                description = detail?.reference ?: "",
                isOsiApproved = detail?.isOsiApproved,
                dependentPath = it.dependentPath,
                isFsfLibre = detail?.isFsfLibre
            )
        }.toList()
        val pageRequest = Pages.ofRequest(result.pageNumber, result.pageSize)
        return Pages.ofResponse(pageRequest, result.totalRecords, reports)
    }

    override fun convertToLoadArguments(request: ArtifactLicensesDetailRequest): LoadResultArguments {
        return ScancodeToolkitResultArguments(
            licenseIds = request.licenseId?.let { listOf(it) } ?: emptyList(),
            riskLevels = request.riskLevel?.let { listOf(it) } ?: emptyList(),
            pageLimit = PageLimit(request.pageNumber, request.pageSize)
        )
    }
}
