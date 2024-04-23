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

package com.tencent.bkrepo.job.migrate.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MigrateFailedNodeDao : SimpleMongoDao<TMigrateFailedNode>() {
    fun findOneToRetry(projectId: String, repoName: String, maxRetryTimes: Int = 3): TMigrateFailedNode? {
        val criteria = Criteria
            .where(TMigrateFailedNode::projectId.name).isEqualTo(projectId)
            .and(TMigrateFailedNode::repoName.name).isEqualTo(repoName)
            .and(TMigrateFailedNode::retryTimes.name).lt(maxRetryTimes)
            .and(TMigrateFailedNode::migrating.name).isEqualTo(false)
        val update = Update()
            .inc(TMigrateFailedNode::retryTimes.name, 1)
            .set(TMigrateFailedNode::lastModifiedDate.name, LocalDateTime.now())
            .set(TMigrateFailedNode::migrating.name, true)
        val query = Query(criteria).with(Sort.by(Sort.Order.asc(TMigrateFailedNode::retryTimes.name)))
        return findAndModify(query, update, FindAndModifyOptions().returnNew(true), TMigrateFailedNode::class.java)
    }

    fun existsFailedNode(projectId: String, repoName: String, fullPath: String? = null): Boolean {
        val criteria = Criteria
            .where(TMigrateFailedNode::projectId.name).isEqualTo(projectId)
            .and(TMigrateFailedNode::repoName.name).isEqualTo(repoName)
        fullPath?.let { criteria.and(TMigrateFailedNode::fullPath.name).isEqualTo(it) }
        return exists(Query(criteria))
    }

    fun resetMigrating(projectId: String, repoName: String, fullPath: String) {
        val criteria = Criteria
            .where(TMigrateFailedNode::projectId.name).isEqualTo(projectId)
            .and(TMigrateFailedNode::repoName.name).isEqualTo(repoName)
            .and(TMigrateFailedNode::fullPath.name).isEqualTo(fullPath)
        updateFirst(Query(criteria), Update.update(TMigrateFailedNode::migrating.name, false))
    }
}
