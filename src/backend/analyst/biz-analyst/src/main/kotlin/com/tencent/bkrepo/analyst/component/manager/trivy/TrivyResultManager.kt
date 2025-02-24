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

package com.tencent.bkrepo.analyst.component.manager.trivy

import com.tencent.bkrepo.analyst.component.manager.AbstractScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.KnowledgeBase
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.TCve
import com.tencent.bkrepo.analyst.component.manager.trivy.dao.VulnerabilityItemDao
import com.tencent.bkrepo.analyst.component.manager.trivy.model.TVulnerabilityItem
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.SaveResultArguments
import com.tencent.bkrepo.analyst.pojo.request.trivy.TrivyLoadResultArguments
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.TrivyScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.trivy.VulnerabilityItem
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component(TrivyScanner.TYPE)
class TrivyResultManager @Autowired constructor(
    private val vulnerabilityItemDao: VulnerabilityItemDao,
    private val knowledgeBase: KnowledgeBase
) : AbstractScanExecutorResultManager() {

    @Transactional(rollbackFor = [Throwable::class])
    override fun save(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        result: ScanExecutorResult,
        arguments: SaveResultArguments?
    ) {
        logger.info("save TrivyScanExecutorResult detail")
        result as TrivyScanExecutorResult
        scanner as TrivyScanner
        val scannerName = scanner.name
        replace(credentialsKey, sha256, scannerName, result.vulnerabilityItems)
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        logger.debug("trivy load, arguments:${arguments?.toJsonString()}")
        scanner as TrivyScanner
        arguments as TrivyLoadResultArguments
        val page = vulnerabilityItemDao.pageBy(credentialsKey, sha256, scanner.name, arguments.pageLimit, arguments)
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records)
    }

    override fun clean(credentialsKey: String?, sha256: String, scannerName: String): Long {
        return vulnerabilityItemDao.deleteBy(credentialsKey, sha256, scannerName).deletedCount
    }

    private fun replace(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        vulnerabilityItems: List<VulnerabilityItem>
    ) {
        val cveSet = HashSet<TCve>(vulnerabilityItems.size)
        val tVulnerabilityItems = ArrayList<TVulnerabilityItem>(vulnerabilityItems.size)

        vulnerabilityItems
            .asSequence()
            .forEach {
                cveSet.add(Converter.convertToCve(it))
                tVulnerabilityItems.add(
                    TVulnerabilityItem(
                        credentialsKey = credentialsKey,
                        sha256 = sha256,
                        scanner = scanner,
                        data = it
                    )
                )
            }

        if (cveSet.isNotEmpty()) {
            knowledgeBase.saveCve(cveSet)
        }
        replace(credentialsKey, sha256, scanner, vulnerabilityItemDao, tVulnerabilityItems)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TrivyResultManager::class.java)
    }
}
