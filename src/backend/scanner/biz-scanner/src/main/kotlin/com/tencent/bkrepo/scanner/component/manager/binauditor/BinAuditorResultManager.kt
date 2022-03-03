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

package com.tencent.bkrepo.scanner.component.manager.binauditor

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.ApplicationItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.CheckSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.CveSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.SensitiveItem
import com.tencent.bkrepo.scanner.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.scanner.component.manager.binauditor.dao.ApplicationItemDao
import com.tencent.bkrepo.scanner.component.manager.binauditor.dao.CheckSecItemDao
import com.tencent.bkrepo.scanner.component.manager.binauditor.dao.CveSecItemDao
import com.tencent.bkrepo.scanner.component.manager.binauditor.dao.ResultItemDao
import com.tencent.bkrepo.scanner.component.manager.binauditor.dao.SensitiveItemDao
import com.tencent.bkrepo.scanner.component.manager.binauditor.model.ResultItem
import com.tencent.bkrepo.scanner.component.manager.binauditor.model.TApplicationItem
import com.tencent.bkrepo.scanner.component.manager.binauditor.model.TCheckSecItem
import com.tencent.bkrepo.scanner.component.manager.binauditor.model.TCveSecItem
import com.tencent.bkrepo.scanner.component.manager.binauditor.model.TSensitiveItem
import com.tencent.bkrepo.scanner.message.ScannerMessageCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component(BinAuditorScanner.TYPE)
class BinAuditorResultManager @Autowired constructor(
    private val checkSecItemDao: CheckSecItemDao,
    private val applicationItemDao: ApplicationItemDao,
    private val sensitiveItemDao: SensitiveItemDao,
    private val cveSecItemDao: CveSecItemDao
) : ScanExecutorResultManager {

    @Transactional(rollbackFor = [Throwable::class])
    override fun save(credentialsKey: String?, sha256: String, scanner: String, result: ScanExecutorResult) {
        result as BinAuditorScanExecutorResult

        result.checkSecItems
            .map { convert<CheckSecItem, TCheckSecItem>(credentialsKey, sha256, scanner, it) }
            .run { replace(credentialsKey, sha256, scanner, checkSecItemDao, this) }

        result.applicationItems
            .map { convert<ApplicationItem, TApplicationItem>(credentialsKey, sha256, scanner, it) }
            .run { replace(credentialsKey, sha256, scanner, applicationItemDao, this) }

        result.sensitiveItems
            .map { convert<SensitiveItem, TSensitiveItem>(credentialsKey, sha256, scanner, it) }
            .run { replace(credentialsKey, sha256, scanner, sensitiveItemDao, this) }

        result.cveSecItems
            .map { convert<CveSecItem, TCveSecItem>(credentialsKey, sha256, scanner, it) }
            .run { replace(credentialsKey, sha256, scanner, cveSecItemDao, this) }
    }

    override fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        type: String?,
        pageLimit: PageLimit?
    ): Any? {
        require(pageLimit != null && type != null)
        CheckSecItem
        val page = when (type) {
            CheckSecItem.TYPE -> checkSecItemDao
            ApplicationItem.TYPE -> applicationItemDao
            SensitiveItem.TYPE -> sensitiveItemDao
            CveSecItem.TYPE -> cveSecItemDao
            else -> {
                throw ErrorCodeException(
                    messageCode = ScannerMessageCode.SCANNER_RESULT_TYPE_INVALID,
                    status = HttpStatus.BAD_REQUEST,
                    params = arrayOf(type)
                )
            }
        }.run { pageBy(credentialsKey, sha256, scanner, pageLimit) }

        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { it.data })
    }

    private inline fun <T, reified R : ResultItem<T>> convert(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        data: T
    ): R {
        return R::class.java.constructors[0].newInstance(null, credentialsKey, sha256, scanner, data) as R
    }

    /**
     * 替换同一文件使用同一扫描器原有的扫描结果
     */
    private fun <T : ResultItem<*>, D : ResultItemDao<T>> replace(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        resultItemDao: D,
        resultItems: List<T>
    ) {
        resultItemDao.deleteBy(credentialsKey, sha256, scanner)
        resultItemDao.insert(resultItems)
    }

}
