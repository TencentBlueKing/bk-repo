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
import com.tencent.bkrepo.analyst.component.manager.standard.model.TLicenseResult
import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.standard.StandardLoadResultArguments
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.LicenseResult
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.stereotype.Repository

@Repository
class LicenseResultDao : ResultItemDao<TLicenseResult>() {
    override fun customizePageBy(criteria: Criteria, arguments: LoadResultArguments): Criteria {
        with(arguments as StandardLoadResultArguments) {
            val andCriteria = ArrayList<Criteria>()

            if (licenseIds.isNotEmpty()) {
                andCriteria.add(Criteria(dataKey(LicenseResult::licenseName.name)).inValues(licenseIds))
            }

            if (rule != null && ignored) {
                ignoreCriteria(rule!!)?.let { criteria -> andCriteria.add(criteria) }
            } else if (rule != null) {
                andCriteria.addAll(activeCriteria(rule!!))
            }

            if (andCriteria.isNotEmpty()) {
                criteria.andOperator(andCriteria)
            }

            return criteria
        }
    }


    private fun activeCriteria(rule: MergedFilterRule): List<Criteria> {
        val andCriteria = ArrayList<Criteria>()

        val ignoreLicenses = rule.ignoreRule.licenses
        val includeLicenses = rule.includeRule.licenses

        if (ignoreLicenses?.isEmpty() == true) {
            andCriteria.add(Criteria(ID).exists(false))
            return andCriteria
        }

        if (ignoreLicenses?.isNotEmpty() == true) {
            andCriteria.add(Criteria(dataKey(LicenseResult::licenseName.name)).not().inValues(ignoreLicenses))
        }

        if (includeLicenses?.isNotEmpty() == true) {
            andCriteria.add(Criteria(dataKey(LicenseResult::licenseName.name)).inValues(includeLicenses))
        }

        return andCriteria
    }

    private fun ignoreCriteria(rule: MergedFilterRule): Criteria? {
        val orCriteria = ArrayList<Criteria>()

        val ignoreLicenses = rule.ignoreRule.licenses
        val includeLicenses = rule.includeRule.licenses

        if (ignoreLicenses?.isEmpty() == true) {
            return null
        } else if (ignoreLicenses?.isNotEmpty() == true) {
            orCriteria.add(Criteria(dataKey(LicenseResult::licenseName.name)).inValues(ignoreLicenses))
        }

        if (includeLicenses?.isNotEmpty() == true) {
            orCriteria.add(Criteria(dataKey(LicenseResult::licenseName.name)).not().inValues(includeLicenses))
        }

        return if (orCriteria.isEmpty()) {
            Criteria().and(ID).exists(false)
        } else {
            Criteria().orOperator(orCriteria)
        }
    }
}
