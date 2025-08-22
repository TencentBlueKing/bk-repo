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

package com.tencent.bkrepo.analyst.component.manager.scancode

import com.tencent.bkrepo.analyst.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.scancode.dao.ScancodeItemDao
import com.tencent.bkrepo.analyst.component.manager.scancode.model.TScancodeItem
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.SaveResultArguments
import com.tencent.bkrepo.analyst.pojo.request.scancodetoolkit.ScancodeToolkitResultArguments
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.result.ScanCodeToolkitScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.scanCodeCheck.scanner.ScancodeToolkitScanner
import com.tencent.bkrepo.common.api.pojo.Page
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component(ScancodeToolkitScanner.TYPE)
class ScancodeResultManager @Autowired constructor(
    private val scancodeItemDao: ScancodeItemDao
) : ScanExecutorResultManager {

    @Transactional(rollbackFor = [Throwable::class])
    override fun save(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        result: ScanExecutorResult,
        arguments: SaveResultArguments?
    ) {
        result as ScanCodeToolkitScanExecutorResult
        scanner as ScancodeToolkitScanner
        val items = result.scancodeItem.map { TScancodeItem(null, credentialsKey, sha256, scanner.name, it) }
        scancodeItemDao.deleteBy(credentialsKey, sha256, scanner.name)
        scancodeItemDao.insert(items)
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any? {
        scanner as ScancodeToolkitScanner
        arguments as ScancodeToolkitResultArguments
        val page = scancodeItemDao.pageBy(credentialsKey, sha256, scanner.name, arguments.pageLimit, arguments)
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { it.data })
    }

    override fun clean(credentialsKey: String?, sha256: String, scannerName: String, batchSize: Int?): Long {
        return scancodeItemDao.deleteBy(credentialsKey, sha256, scannerName, batchSize).deletedCount
    }
}
