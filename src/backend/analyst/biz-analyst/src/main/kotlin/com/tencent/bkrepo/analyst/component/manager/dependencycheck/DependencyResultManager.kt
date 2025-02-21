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

package com.tencent.bkrepo.analyst.component.manager.dependencycheck

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.scanner.DependencyScanner
import com.tencent.bkrepo.analyst.component.manager.AbstractScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.dependencycheck.dao.DependencyItemDao
import com.tencent.bkrepo.analyst.component.manager.dependencycheck.model.TDependencyItem
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.KnowledgeBase
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.TCve
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.SaveResultArguments
import com.tencent.bkrepo.analyst.pojo.request.dependencecheck.DependencyLoadResultArguments
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component(DependencyScanner.TYPE)
class DependencyResultManager @Autowired constructor(
    private val dependencyItemDao: DependencyItemDao,
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
        result as DependencyScanExecutorResult
        scanner as DependencyScanner
        val scannerName = scanner.name
        replace(credentialsKey, sha256, scannerName, result.dependencyItems)
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        if (logger.isDebugEnabled) {
            logger.debug("DependencyCheck load, arguments:${arguments?.toJsonString()}")
        }
        scanner as DependencyScanner
        arguments as DependencyLoadResultArguments
        val page = dependencyItemDao.pageBy(credentialsKey, sha256, scanner.name, arguments.pageLimit, arguments)
        val pocIds = page.records.map { Converter.pocIdOf(it.data.cveId) }
        val cveMap = knowledgeBase.findByPocId(pocIds).associateBy { it.pocId }
        val records = page.records.map { Converter.convert(it, cveMap[Converter.pocIdOf(it.data.cveId)]!!) }
        return Page(page.pageNumber, page.pageSize, page.totalRecords, records)
    }

    override fun clean(credentialsKey: String?, sha256: String, scanner: Scanner): Long {
        return dependencyItemDao.deleteBy(credentialsKey, sha256, scanner.name).deletedCount
    }

    private fun replace(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        dependencyItems: List<DependencyItem>
    ) {
        val cveSet = HashSet<TCve>(dependencyItems.size)
        val tDependencyItems = ArrayList<TDependencyItem>(dependencyItems.size)

        dependencyItems
            .asSequence()
            .forEach {
                cveSet.add(Converter.convertToCve(it))
                tDependencyItems.add(
                    TDependencyItem(
                        credentialsKey = credentialsKey,
                        sha256 = sha256,
                        scanner = scanner,
                        data = Converter.convert(it)
                    )
                )
            }

        if (cveSet.isNotEmpty()) {
            knowledgeBase.saveCve(cveSet)
        }
        replace(credentialsKey, sha256, scanner, dependencyItemDao, tDependencyItems)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DependencyResultManager::class.java)
    }
}
