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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.dao.ScannerDao
import com.tencent.bkrepo.analyst.exception.ScannerNotFoundException
import com.tencent.bkrepo.analyst.model.TScanner
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScannerServiceImpl @Autowired constructor(
    private val scannerDao: ScannerDao
) : ScannerService {
    override fun create(scanner: Scanner): Scanner {
        with(scanner) {
            if (scannerDao.existsByName(name)) {
                return update(scanner)
            }
            val now = LocalDateTime.now()
            val userId = SecurityUtils.getUserId()
            return scannerDao.insert(
                TScanner(
                    createdBy = userId,
                    createdDate = now,
                    lastModifiedBy = userId,
                    lastModifiedDate = now,
                    name = name,
                    type = scanner.type,
                    version = scanner.version,
                    description = scanner.description,
                    config = scanner.toJsonString(),
                    supportFileNameExt = scanner.supportFileNameExt,
                    supportPackageTypes = scanner.supportPackageTypes,
                    supportScanTypes = scanner.supportScanTypes
                )
            ).run { convert(this) }
        }
    }

    override fun update(scanner: Scanner): Scanner {
        with(scanner) {
            val userId = SecurityUtils.getUserId()
            val savedScanner = scannerDao.findByName(name) ?: throw ScannerNotFoundException(name)
            return scannerDao.save(
                savedScanner.copy(
                    lastModifiedBy = userId,
                    lastModifiedDate = LocalDateTime.now(),
                    version = version,
                    description = description,
                    config = scanner.toJsonString(),
                    supportFileNameExt = supportFileNameExt,
                    supportPackageTypes = supportPackageTypes,
                    supportScanTypes = supportScanTypes
                )
            ).run { convert(this) }
        }
    }

    override fun get(name: String): Scanner {
        return find(name) ?: throw ScannerNotFoundException(name)
    }

    override fun default(): Scanner {
        return scannerDao.findOne(Query().limit(1))?.run { convert(this) }
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, "default scanner")
    }

    override fun find(name: String): Scanner? {
        return scannerDao.findByName(name)?.run { convert(this) }
    }

    override fun find(names: List<String>): List<Scanner> {
        return scannerDao.findByNames(names).map { convert(it) }
    }

    override fun find(packageType: String?, scanType: String?): List<Scanner> {
        return scannerDao.find(packageType, scanType).map { convert(it) }
    }

    override fun supportFileNameExt(): Set<String> {
        return scannerDao.list().flatMap { it.supportFileNameExt }.toSet()
    }

    override fun supportPackageType(): Set<String> {
        return scannerDao.list().flatMap { it.supportPackageTypes }.toSet()
    }

    override fun list(): List<Scanner> {
        return scannerDao.list().map { convert(it) }
    }

    override fun delete(name: String) {
        if (scannerDao.deleteByName(name).modifiedCount == 0L) {
            throw ScannerNotFoundException(name)
        }
    }

    private fun convert(scanner: TScanner): Scanner {
        return scanner.config.readJsonString()
    }
}
