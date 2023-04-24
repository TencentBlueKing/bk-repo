/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.analyst.component.manager.ResultItemDao.Companion.dataKey
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResultData
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.inValues

class SecurityFilterCriteriaBuilder(rule: MergedFilterRule?, ignored: Boolean) : FilterCriteriaBuilder(rule, ignored) {

    override fun activeCriteria(rule: MergedFilterRule): List<Criteria> {
        val ignoreVulIds = rule.ignoreRule.vulIds
        val includeVulIds = rule.includeRule.vulIds
        val minSeverityLevel = rule.minSeverityLevel

        val criteriaList = ArrayList<Criteria>()
        if (ignoreVulIds?.isEmpty() == true) {
            // ignoreVulIds为空集合时表示忽略所有
            // 设置一个永远为false的条件，让数据库查询结果返回空
            criteriaList.add(Criteria(AbstractMongoDao.ID).exists(false))
            return criteriaList
        }

        if (ignoreVulIds?.isNotEmpty() == true) {
            criteriaList.add(Criteria(dataKey(TSecurityResultData::vulId.name)).not().inValues(ignoreVulIds))
            criteriaList.add(Criteria(dataKey(TSecurityResultData::cveId.name)).not().inValues(ignoreVulIds))
        }

        if (!includeVulIds.isNullOrEmpty()) {
            val criteria = Criteria().orOperator(
                Criteria(dataKey(TSecurityResultData::vulId.name)).inValues(includeVulIds),
                Criteria(dataKey(TSecurityResultData::cveId.name)).inValues(includeVulIds)
            )
            criteriaList.add(criteria)
        }

        if (!rule.ignoreRule.riskyPackageKeys.isNullOrEmpty()) {
            criteriaList.add(
                Criteria(dataKey(TSecurityResultData::pkgName.name)).not().inValues(rule.ignoreRule.riskyPackageKeys!!)
            )
        }

        if (!rule.includeRule.riskyPackageKeys.isNullOrEmpty()) {
            criteriaList.add(
                Criteria(dataKey(TSecurityResultData::pkgName.name)).inValues(rule.includeRule.riskyPackageKeys!!)
            )
        }

        if (minSeverityLevel != null && minSeverityLevel != Level.LOW.level) {
            criteriaList.add(Criteria(dataKey(TSecurityResultData::severityLevel.name)).gte(minSeverityLevel))
        }
        return criteriaList
    }

    override fun ignoreCriteria(rule: MergedFilterRule): Criteria? {
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

        if (rule.ignoreRule.riskyPackageKeys?.isNotEmpty() == true) {
            orCriteria.add(
                Criteria(dataKey(TSecurityResultData::pkgName.name)).inValues(rule.ignoreRule.riskyPackageKeys!!)
            )
        }

        if (rule.includeRule.riskyPackageKeys?.isNotEmpty() == true) {
            orCriteria.add(
                Criteria(dataKey(TSecurityResultData::pkgName.name)).not().inValues(rule.includeRule.riskyPackageKeys!!)
            )
        }

        if (minSeverityLevel != null && minSeverityLevel != Level.LOW.level) {
            orCriteria.add(Criteria(dataKey(TSecurityResultData::severityLevel.name)).lt(minSeverityLevel))
        }

        return if (orCriteria.isEmpty()) {
            // 没有忽略条件时设置一个永远未false的条件，使查询返回空集合
            Criteria().and(AbstractMongoDao.ID).exists(false)
        } else {
            Criteria().orOperator(orCriteria)
        }
    }
}
