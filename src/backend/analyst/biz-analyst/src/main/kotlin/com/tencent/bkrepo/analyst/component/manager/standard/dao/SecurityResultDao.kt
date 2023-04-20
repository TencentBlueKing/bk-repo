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

package com.tencent.bkrepo.analyst.component.manager.standard.dao

import com.tencent.bkrepo.analyst.component.manager.ResultItemDao
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResult
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResultData
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.standard.StandardLoadResultArguments
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.stereotype.Repository

@Repository
class SecurityResultDao : ResultItemDao<TSecurityResult>() {
    override fun customizePageBy(criteria: Criteria, arguments: LoadResultArguments): Criteria {
        with(arguments as StandardLoadResultArguments) {
            val andCriteria = ArrayList<Criteria>()
            if (vulnerabilityLevels.isNotEmpty()) {
                andCriteria.add(Criteria(dataKey(TSecurityResultData::severity.name)).inValues(vulnerabilityLevels))
            }

            if (vulIds.isNotEmpty()) {
                andCriteria.add(
                    Criteria().orOperator(
                        Criteria(dataKey(TSecurityResultData::vulId.name)).inValues(vulIds),
                        Criteria(dataKey(TSecurityResultData::cveId.name)).inValues(vulIds)
                    )
                )
            }

            rule?.let {
                if (ignored) {
                    ignoreCriteria(it)?.let { criteria -> andCriteria.add(criteria) }
                } else {
                    andCriteria.addAll(activeCriteria(it))
                }
            }

            if (andCriteria.isNotEmpty()) {
                criteria.andOperator(andCriteria)
            }

            return criteria
        }
    }

    override fun customizeQuery(query: Query, arguments: LoadResultArguments): Query {
        query.with(Sort.by(Sort.Direction.DESC, dataKey(TSecurityResultData::severityLevel.name)))
        return query
    }


    private fun activeCriteria(rule: MergedFilterRule): List<Criteria> {
        val ignoreVulIds = rule.ignoreRule.vulIds
        val includeVulIds = rule.includeRule.vulIds
        val minSeverityLevel = rule.minSeverityLevel

        val criteriaList = ArrayList<Criteria>()
        if (ignoreVulIds?.isEmpty() == true) {
            // ignoreVulIds为空集合时表示忽略所有
            // 设置一个永远为false的条件，让数据库查询结果返回空
            criteriaList.add(Criteria(ID).exists(false))
            return criteriaList
        }

        if (ignoreVulIds?.isNotEmpty() == true) {
            criteriaList.add(Criteria(dataKey(TSecurityResultData::vulId.name)).not().inValues(ignoreVulIds))
            criteriaList.add(Criteria(dataKey(TSecurityResultData::cveId.name)).not().inValues(ignoreVulIds))
        }

        if (!includeVulIds.isNullOrEmpty()) {
            criteriaList.add(Criteria(dataKey(TSecurityResultData::vulId.name)).inValues(includeVulIds))
            criteriaList.add(Criteria(dataKey(TSecurityResultData::cveId.name)).inValues(includeVulIds))
        }

        if (minSeverityLevel != null && minSeverityLevel != Level.LOW.level) {
            criteriaList.add(Criteria(dataKey(TSecurityResultData::severityLevel.name)).gte(minSeverityLevel))
        }
        return criteriaList
    }

    private fun ignoreCriteria(rule: MergedFilterRule): Criteria? {
        val ignoreVulIds = rule.ignoreRule.vulIds
        val includeVulIds = rule.includeRule.vulIds
        val minSeverityLevel = rule.minSeverityLevel

        val orCriteria = ArrayList<Criteria>()
        if (ignoreVulIds?.isNotEmpty() == true) {
            orCriteria.add(Criteria(dataKey(TSecurityResultData::vulId.name)).inValues(ignoreVulIds))
            orCriteria.add(Criteria(dataKey(TSecurityResultData::cveId.name)).inValues(ignoreVulIds))
        } else if (ignoreVulIds?.isEmpty() == true) {
            // ignoreVulIds为空集合时表示忽略所有
            // 此处要返回所有被忽略的漏洞相当于返回所有漏洞，可以不设置忽略条件直接返回，直接返回
            return null
        }

        if (!includeVulIds.isNullOrEmpty()) {
            orCriteria.add(
                Criteria().andOperator(
                    Criteria(dataKey(TSecurityResultData::vulId.name)).not().inValues(includeVulIds),
                    Criteria(dataKey(TSecurityResultData::cveId.name)).not().inValues(includeVulIds)
                )
            )
        }

        if (minSeverityLevel != null && minSeverityLevel != Level.LOW.level) {
            orCriteria.add(Criteria(dataKey(TSecurityResultData::severityLevel.name)).lt(minSeverityLevel))
        }

        return if (orCriteria.isEmpty()) {
            // 没有忽略条件时设置一个永远未false的条件，使查询返回空集合
            Criteria().and(ID).exists(false)
        } else {
            Criteria().orOperator(orCriteria)
        }
    }
}
