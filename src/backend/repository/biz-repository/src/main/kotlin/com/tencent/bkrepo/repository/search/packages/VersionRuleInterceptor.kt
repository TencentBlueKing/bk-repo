/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.search.packages

import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao.Companion.ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * 版本条件规则拦截器
 * 1. 将传入规则转换为package_version表的查询条件
 * 2. 将version查询结果转换为packageId查询规则, 作为package查询的筛选条件
 */
abstract class VersionRuleInterceptor(
    open val packageVersionDao: PackageVersionDao
) : QueryRuleInterceptor {

    abstract fun getVersionCriteria(rule: Rule, context: PackageQueryContext): Criteria

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        require(context is PackageQueryContext)
        // 查找符合条件的version
        val versionQuery = Query(getVersionCriteria(rule, context))
        val versionMap = queryRecords(versionQuery) { query -> packageVersionDao.find(query) }
            .groupBy({ it.packageId }, { it.name })
        // 多个版本查询规则在同一个package的版本号交集
        for ((key, value) in versionMap) {
            context.matchedVersions.putIfAbsent(key, value.toMutableSet())?.retainAll(value.toSet())
        }
        val emptyKeys = context.matchedVersions.filterValues { it.isEmpty() }.keys
        // 构建packageId查询规则
        val packageIdList = (versionMap.keys - emptyKeys).toList()
        val newRule = if (packageIdList.size == 1) {
            Rule.QueryRule(ID, packageIdList.first(), OperationType.EQ)
        } else {
            Rule.QueryRule(ID, packageIdList, OperationType.IN)
        }
        return context.interpreter.resolveRule(newRule.toFixed(), context)
    }

    protected fun <T> queryRecords(query: Query, execFind: (Query) -> List<T>): List<T> {
        val records = mutableListOf<T>()
        var pageNumber = 1
        do {
            val pageRequest = Pages.ofRequest(pageNumber, PAGE_SIZE)
            val page = execFind(query.with(pageRequest))
            records.addAll(page)
            pageNumber++
        } while (page.size == PAGE_SIZE)
        return records
    }

    companion object {
        const val PAGE_SIZE = 1000
    }
}
