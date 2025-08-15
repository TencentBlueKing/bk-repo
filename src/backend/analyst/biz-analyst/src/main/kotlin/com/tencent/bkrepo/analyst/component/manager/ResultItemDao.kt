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

package com.tencent.bkrepo.analyst.component.manager

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.analyst.dao.ScannerSimpleMongoDao
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

abstract class ResultItemDao<T : ResultItem<*>> : ScannerSimpleMongoDao<T>() {

    fun deleteBy(credentialsKey: String?, sha256: String, scanner: String, batchSize: Int? = null): DeleteResult {
        val query = Query(buildCriteria(credentialsKey, sha256, scanner))
        batchSize?.let { query.limit(it) }
        return remove(query)
    }

    fun pageBy(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        pageLimit: PageLimit,
        arguments: LoadResultArguments
    ): Page<T> {
        val pageable = PageRequest.of(pageLimit.pageNumber - 1, pageLimit.pageSize)
        val criteria = customizePageBy(buildCriteria(credentialsKey, sha256, scanner), arguments)
        val query = customizeQuery(Query(criteria).with(pageable), arguments)
        val total = count(Query.of(query).limit(0).skip(0))
        val data = find(query)
        return Page(pageLimit.pageNumber, pageLimit.pageSize, total, data)
    }

    /**
     * 将符合条件的数据全部查出后在[toPage]方法中进一步过滤并返回分页结果
     */
    fun list(
        credentialsKey: String?,
        sha256: String,
        scanner: String,
        pageLimit: PageLimit,
        arguments: LoadResultArguments
    ): Page<T> {
        val criteria = customizePageBy(buildCriteria(credentialsKey, sha256, scanner), arguments)
        val query = customizeQuery(Query(criteria), arguments)
        return toPage(find(query), pageLimit, arguments)
    }

    protected open fun customizeQuery(query: Query, arguments: LoadResultArguments): Query {
        return query
    }

    protected open fun customizePageBy(criteria: Criteria, arguments: LoadResultArguments): Criteria {
        return criteria
    }

    protected open fun toPage(records: List<T>, pageLimit: PageLimit, arguments: LoadResultArguments): Page<T> {
        val total = records.size.toLong()
        val start = (pageLimit.pageNumber - 1) * pageLimit.pageSize
        val end = minOf(start + pageLimit.pageSize, records.size)
        val pagedRecords = if (start >= records.size) {
            emptyList()
        } else {
            records.subList(start, end)
        }
        return Page(pageLimit.pageNumber, pageLimit.pageSize, total, pagedRecords)
    }

    private fun buildCriteria(credentialsKey: String?, sha256: String, scanner: String): Criteria {
        return Criteria
            .where(ResultItem<*>::credentialsKey.name).isEqualTo(credentialsKey)
            .and(ResultItem<*>::sha256.name).isEqualTo(sha256)
            .and(ResultItem<*>::scanner.name).isEqualTo(scanner)
    }

    companion object {
        fun dataKey(name: String) = "${ResultItem<*>::data.name}.$name"
    }
}
