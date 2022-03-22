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

package com.tencent.bkrepo.scanner.dao

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScanPlanDao : SimpleMongoDao<TScanPlan>() {
    fun find(projectId: String, id: String): TScanPlan? {
        val criteria = projectCriteria(projectId).and(TScanPlan::id.name).isEqualTo(id)
        return findOne(Query(criteria))
    }

    fun exists(projectId: String, id: String): Boolean {
        val criteria = projectCriteria(projectId).and(TScanPlan::id.name).isEqualTo(id)
        return exists(Query(criteria))
    }

    fun delete(projectId: String, id: String): UpdateResult {
        val criteria = projectCriteria(projectId).and(TScanPlan::id.name).isEqualTo(id)
        val now = LocalDateTime.now()
        val update = update(now).set(TScanPlan::deleted.name, now)
        return updateFirst(Query(criteria), update)
    }

    fun existsByProjectIdAndName(projectId: String, name: String): Boolean {
        val query = Query(
            projectCriteria(projectId).and(TScanPlan::name.name).isEqualTo(name)
        )
        return exists(query)
    }

    fun list(projectId: String, type: String? = null): List<TScanPlan> {
        val criteria = projectCriteria(projectId)
        type?.let { criteria.and(TScanPlan::type.name).isEqualTo(type) }
        val query = Query(criteria).with(Sort.by(TScanPlan::createdDate.name).descending())
        return find(query)
    }

    fun page(projectId: String, type: String?, planNameContains: String?, pageLimit: PageLimit): Page<TScanPlan> {
        val criteria = projectCriteria(projectId)
        type?.let { criteria.and(TScanPlan::type.name).isEqualTo(type) }
        planNameContains?.let { criteria.and(TScanPlan::name.name).regex(".*$planNameContains.*") }
        val pageRequest = Pages.ofRequest(pageLimit.getNormalizedPageNumber(), pageLimit.getNormalizedPageSize())
        val query = Query(criteria).with(pageRequest).with(Sort.by(TScanPlan::createdDate.name).descending())

        return Pages.ofResponse(pageRequest, count(query), find(query))
    }

    fun update(scanPlan: ScanPlan): UpdateResult {
        with(scanPlan) {
            val criteria = projectCriteria(projectId!!).and(TScanPlan::id.name).`is`(id)
            val update = update()
            name?.let { update.set(TScanPlan::name.name, it) }
            description?.let { update.set(TScanPlan::description.name, it) }
            scanOnNewArtifact?.let { update.set(TScanPlan::scanOnNewArtifact.name, it) }
            repoNames?.let { update.set(TScanPlan::repoNames.name, it) }
            rule?.let { update.set(TScanPlan::rule.name, it.toJsonString()) }

            val query = Query(criteria)
            return updateFirst(query, update)
        }
    }

    private fun projectCriteria(projectId: String, includeDeleted: Boolean = false): Criteria {
        val criteria = TScanPlan::projectId.isEqualTo(projectId)
        if (!includeDeleted) {
            criteria.and(TScanPlan::deleted.name).isEqualTo(null)
        }
        return criteria
    }

    private fun update(
        now: LocalDateTime = LocalDateTime.now(),
        lastModifiedBy: String = SecurityUtils.getUserId()
    ): Update {
        return Update.update(TScanPlan::lastModifiedDate.name, now)
            .set(TScanPlan::lastModifiedBy.name, lastModifiedBy)
    }
}
