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

package com.tencent.bkrepo.analyst.component.manager.knowledgebase

import com.tencent.bkrepo.analyst.dao.ScannerSimpleMongoDao
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.BulkOperationException
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.insert
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class CveDao : ScannerSimpleMongoDao<TCve>() {
    fun findByCveId(cveId: String): TCve? {
        val query = Query(TCve::cveId.isEqualTo(cveId))
        return findOne(query)
    }

    fun findByPocId(pocId: String): TCve? {
        return findOne(Query(TCve::pocId.isEqualTo(pocId)))
    }

    fun findByCveIds(cveIds: Collection<String>): List<TCve> {
        val query = Query(TCve::cveId.inValues(cveIds))
        return find(query)
    }

    fun findByPocIds(pocIds: Collection<String>): List<TCve> {
        return find(Query(TCve::pocId.inValues(pocIds)))
    }

    fun saveIfNotExists(cve: TCve) {
        if (!exists(Query(TCve::cveId.isEqualTo(cve.cveId)))) {
            try {
                insert(cve)
            } catch (ignore: DuplicateKeyException) {
            }
        }
    }

    fun saveIfNotExists(cveList: Collection<TCve>) {
        try {
            determineMongoTemplate()
                .insert<TCve>()
                .withBulkMode(BulkOperations.BulkMode.UNORDERED)
                .bulk(cveList)
        } catch (ignore: DuplicateKeyException) {
        } catch (ignore: BulkOperationException) {
        }
    }
}
