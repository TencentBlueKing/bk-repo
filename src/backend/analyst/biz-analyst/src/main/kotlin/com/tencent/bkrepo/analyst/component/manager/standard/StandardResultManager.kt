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

package com.tencent.bkrepo.analyst.component.manager.standard

import com.tencent.bkrepo.analyst.component.manager.AbstractScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.arrowhead.Converter
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.KnowledgeBase
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.TCve
import com.tencent.bkrepo.analyst.component.manager.standard.dao.LicenseResultDao
import com.tencent.bkrepo.analyst.component.manager.standard.dao.SecurityResultDao
import com.tencent.bkrepo.analyst.component.manager.standard.dao.SensitiveResultDao
import com.tencent.bkrepo.analyst.component.manager.standard.model.TLicenseResult
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResult
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSensitiveResult
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.SaveResultArguments
import com.tencent.bkrepo.analyst.pojo.request.standard.StandardLoadResultArguments
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanType
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.LicenseResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SecurityResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.stereotype.Component

@Component(StandardScanner.TYPE)
class StandardResultManager(
    private val securityResultDao: SecurityResultDao,
    private val licenseResultDao: LicenseResultDao,
    private val sensitiveResultDao: SensitiveResultDao,
    private val knowledgeBase: KnowledgeBase
) : AbstractScanExecutorResultManager() {
    override fun save(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        result: ScanExecutorResult,
        arguments: SaveResultArguments?
    ) {
        result as StandardScanExecutorResult
        scanner as StandardScanner

        // 更新安全漏洞结果
        result.output?.result?.securityResults?.let {
            replaceSecurityResult(credentialsKey, sha256, scanner.name, it)
        }

        // 更新License结果
        result.output?.result?.licenseResults?.map {
            TLicenseResult(credentialsKey = credentialsKey, sha256 = sha256, scanner = scanner.name, data = it)
        }?.let {
            replace(credentialsKey, sha256, scanner.name, licenseResultDao, it)
        }

        result.output?.result?.sensitiveResults?.map {
            TSensitiveResult(credentialsKey = credentialsKey, sha256 = sha256, scanner = scanner.name, data = it)
        }?.let {
            replace(credentialsKey, sha256, scanner.name, sensitiveResultDao, it)
        }
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        arguments as StandardLoadResultArguments
        return when (arguments.reportType) {
            ScanType.SECURITY.name -> loadSecurityItems(credentialsKey, sha256, scanner, arguments.pageLimit, arguments)
            ScanType.LICENSE.name -> loadLicenseResults(credentialsKey, sha256, scanner, arguments.pageLimit, arguments)
            ScanType.SENSITIVE.name -> {
                sensitiveResultDao.pageBy(credentialsKey, sha256, scanner.name, arguments.pageLimit, arguments).let {
                    Page(it.pageNumber, it.pageSize, it.totalRecords, it.records.map { record -> record.data })
                }
            }

            else -> throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, arguments.reportType)
        }
    }

    override fun clean(credentialsKey: String?, sha256: String, scannerName: String, batchSize: Int?): Long {
        val resultItemDaoList = listOf(securityResultDao, licenseResultDao, sensitiveResultDao)
        return clean(resultItemDaoList, credentialsKey, sha256, scannerName, batchSize)
    }

    private fun loadLicenseResults(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        pageLimit: PageLimit,
        arguments: StandardLoadResultArguments
    ): Page<LicenseResult> {
        val page = licenseResultDao.pageBy(credentialsKey, sha256, scanner.name, pageLimit, arguments)
        val records = page.records.map { it.data }
        return Page(page.pageNumber, page.pageSize, page.totalRecords, records)
    }

    private fun loadSecurityItems(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        pageLimit: PageLimit,
        arguments: StandardLoadResultArguments
    ): Page<SecurityResult> {
        val page = if (arguments.rule?.isEmpty() == false) {
            // 由于组件版本范围查询较为复杂，无法在数据库查询语句中实现，因此将数据全部查出，在服务中过滤符合范围条件的组件漏洞
            securityResultDao.list(credentialsKey, sha256, scanner.name, pageLimit, arguments)
        } else if (arguments.ignored) {
            Page(pageLimit.pageNumber, pageLimit.pageSize, 0, emptyList())
        } else {
            securityResultDao.pageBy(credentialsKey, sha256, scanner.name, pageLimit, arguments)
        }
        val cveMap = page.records.map { it.data.vulId }.let { knowledgeBase.findByPocId(it) }.associateBy { it.pocId }
        val records = page.records.map { Converter.convert(it, cveMap[it.data.vulId]) }
        return Page(page.pageNumber, page.pageSize, page.totalRecords, records)
    }

    private fun replaceSecurityResult(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        securityResults: List<SecurityResult>
    ) {
        val cveSet = HashSet<TCve>(securityResults.size)
        val tSecurityResult = ArrayList<TSecurityResult>(securityResults.size)

        securityResults
            .asSequence()
            .forEach {
                cveSet.add(Converter.convertToCve(it))
                tSecurityResult.add(
                    TSecurityResult(
                        credentialsKey = credentialsKey,
                        sha256 = sha256,
                        scanner = scanner,
                        data = Converter.convert(it)
                    )
                )
            }

        // TODO 改为使用远程知识库
        if (cveSet.isNotEmpty()) {
            knowledgeBase.saveCve(cveSet)
        }
        replace(credentialsKey, sha256, scanner, securityResultDao, tSecurityResult)
    }
}
