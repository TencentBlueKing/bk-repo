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

package com.tencent.bkrepo.analyst.dao

import com.tencent.bkrepo.analyst.model.TIgnoreRule
import com.tencent.bkrepo.analyst.model.TIgnoreRule.Companion.SYSTEM_PROJECT_ID
import com.tencent.bkrepo.analyst.pojo.request.ignore.MatchIgnoreRuleRequest
import com.tencent.bkrepo.analyst.pojo.request.ignore.UpdateIgnoreRuleRequest
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class IgnoreRuleDao : ScannerSimpleMongoDao<TIgnoreRule>() {
    fun exists(projectId: String, name: String): Boolean {
        val query = Query(
            Criteria
                .where(TIgnoreRule::projectId.name).isEqualTo(projectId)
                .and(TIgnoreRule::name.name).isEqualTo(name)
        )
        return exists(query)
    }

    fun list(projectId: String, planId: String?, pageRequest: PageRequest): Page<TIgnoreRule> {
        val criteria = Criteria.where(TIgnoreRule::projectId.name).isEqualTo(projectId)
        planId?.let { criteria.and(TIgnoreRule::planId.name).isEqualTo(planId) }
        return page(Query(criteria), pageRequest)
    }

    fun remove(projectId: String, ruleId: String): Boolean {
        val query = Query(
            Criteria.where(TIgnoreRule::projectId.name).isEqualTo(projectId).and(ID).isEqualTo(ruleId)
        )
        return remove(query).deletedCount == 1L
    }

    fun match(request: MatchIgnoreRuleRequest): List<TIgnoreRule> {
        with(request) {
            val criteria = Criteria().orOperator(
                // 查询系统级规则
                Criteria
                    .where(TIgnoreRule::projectId.name).isEqualTo(SYSTEM_PROJECT_ID)
                    .and(TIgnoreRule::projectIds.name).inValues(projectId),
                // 查询项目级规则
                Criteria().and(TIgnoreRule::projectId.name).isEqualTo(projectId)
            ).andOperator(
                // 仓库匹配
                notExistsOrEqual(TIgnoreRule::repoName.name, repoName),
                // 扫描方案id匹配
                notExistsOrEqual(TIgnoreRule::planId.name, planId)
            )

            return find(Query(criteria))
        }
    }

    fun update(req: UpdateIgnoreRuleRequest): TIgnoreRule? {
        with(req) {
            val query = Query(Criteria.where(TIgnoreRule::projectId.name).isEqualTo(projectId).and(ID).isEqualTo(id))
            val update = Update()
                .set(TIgnoreRule::name.name, name)
                .set(TIgnoreRule::description.name, description)
                .set(TIgnoreRule::lastModifiedBy.name, SecurityUtils.getUserId())
                .set(TIgnoreRule::lastModifiedDate.name, LocalDateTime.now())
                .set(TIgnoreRule::repoName.name, repoName)
                .set(TIgnoreRule::planId.name, planId)
                .set(TIgnoreRule::fullPath.name, fullPath)
                .set(TIgnoreRule::packageKey.name, packageKey)
                .set(TIgnoreRule::packageVersion.name, packageVersion)
                .set(TIgnoreRule::vulIds.name, vulIds)
                .set(TIgnoreRule::licenseNames.name, licenseNames)
            if (projectId == SYSTEM_PROJECT_ID) {
                update.set(TIgnoreRule::projectIds.name, projectIds)
            }
            updateFirst(query, update)
            return findOne(query)
        }
    }

    private fun notExistsOrEqual(name: String, value: String?): Criteria {
        val orCriteria = ArrayList<Criteria>()
        orCriteria.add(Criteria.where(name).isEqualTo(null))
        orCriteria.add(Criteria.where(name).exists(false))
        value?.let { orCriteria.add(Criteria.where(name).isEqualTo(value)) }
        return Criteria().orOperator(orCriteria)
    }
}
