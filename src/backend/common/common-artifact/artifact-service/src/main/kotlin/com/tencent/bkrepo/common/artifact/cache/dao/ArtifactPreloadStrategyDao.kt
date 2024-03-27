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
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyUpdateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ArtifactPreloadStrategyDao : SimpleMongoDao<TArtifactPreloadStrategy>() {
    fun update(request: ArtifactPreloadStrategyUpdateRequest): UpdateResult {
        with(request) {
            val criteria = build(projectId, repoName)
                .and(ID).isEqualTo(id)
                .and(TArtifactPreloadStrategy::type.name).ne(PreloadStrategyType.INTELLIGENT.name)
            val update = Update()
            update.set(TArtifactPreloadStrategy::lastModifiedBy.name, operator)
            update.set(TArtifactPreloadStrategy::lastModifiedDate.name, LocalDateTime.now())
            fullPathRegex?.let { update.set(TArtifactPreloadStrategy::fullPathRegex.name, it) }
            recentSeconds?.let { update.set(TArtifactPreloadStrategy::recentSeconds.name, it) }
            preloadCron?.let { update.set(TArtifactPreloadStrategy::preloadCron.name, it) }
            return updateFirst(Query(criteria), update)
        }
    }

    fun delete(projectId: String, repoName: String, id: String): DeleteResult {
        val criteria = build(projectId, repoName).and(ID).isEqualTo(id)
        return remove(Query(criteria))
    }

    fun list(projectId: String, repoName: String): List<TArtifactPreloadStrategy> {
        return find(Query(build(projectId, repoName)))
    }

    fun count(projectId: String, repoName: String): Long {
        return count(Query(build(projectId, repoName)))
    }

    private fun build(projectId: String, repoName: String) = Criteria
        .where(TArtifactPreloadStrategy::projectId.name).isEqualTo(projectId)
        .and(TArtifactPreloadStrategy::repoName.name).isEqualTo(repoName)
}
