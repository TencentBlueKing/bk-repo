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

package com.tencent.bkrepo.common.artifact.cache.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ArtifactPreloadPlanDao : SimpleMongoDao<TArtifactPreloadPlan>() {
    fun page(projectId: String, repoName: String, request: PageRequest): List<TArtifactPreloadPlan> {
        val criteria = buildCriteria(projectId, repoName)
        return find(Query(criteria).with(request))
    }

    fun remove(projectId: String, repoName: String, id: String): DeleteResult {
        return remove(Query(buildCriteria(projectId, repoName).and(ID).isEqualTo(id)))
    }

    fun remove(projectId: String, repoName: String): DeleteResult {
        return remove(Query(buildCriteria(projectId, repoName)))
    }

    fun remove(id: String): DeleteResult {
        return remove(Query(Criteria.where(ID).isEqualTo(id)))
    }

    fun listReadyPlans(limit: Int): List<TArtifactPreloadPlan> {
        val now = System.currentTimeMillis()
        val criteria = Criteria.where(TArtifactPreloadPlan::status.name)
            .isEqualTo(ArtifactPreloadPlan.STATUS_PENDING)
            .and(TArtifactPreloadPlan::executeTime.name).lte(now)
        return find(Query(criteria).limit(limit))
    }

    fun updateStatus(planId: String, status: String, lastModifiedDate: LocalDateTime): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(planId)
            .and(TArtifactPreloadPlan::lastModifiedDate.name).isEqualTo(lastModifiedDate)
        val update = Update.update(TArtifactPreloadPlan::status.name, status)
        return updateFirst(Query(criteria), update)
    }

    private fun buildCriteria(projectId: String, repoName: String) =
        TArtifactPreloadPlan::projectId.isEqualTo(projectId)
            .and(TArtifactPreloadPlan::repoName.name).isEqualTo(repoName)
}
