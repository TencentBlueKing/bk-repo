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

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MigrateRepoStorageTaskDao : SimpleMongoDao<TMigrateRepoStorageTask>() {
    fun exists(projectId: String, repoName: String): Boolean {
        val criteria = TMigrateRepoStorageTask::projectId.isEqualTo(projectId)
            .and(TMigrateRepoStorageTask::repoName.name).isEqualTo(repoName)
        return exists(Query(criteria))
    }

    fun find(projectId: String, repoName: String): TMigrateRepoStorageTask? {
        val criteria = TMigrateRepoStorageTask::projectId.isEqualTo(projectId)
            .and(TMigrateRepoStorageTask::repoName.name).isEqualTo(repoName)
        return findOne(Query(criteria))
    }

    fun updateStartDate(id: String, startDate: LocalDateTime) {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val update = Update()
            .set(TMigrateRepoStorageTask::startDate.name, startDate)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
        updateFirst(Query(criteria), update)
    }

    fun updateState(
        id: String,
        sourceState: String,
        targetState: String,
        lastModifiedDate: LocalDateTime
    ): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
            .and(TMigrateRepoStorageTask::state.name).isEqualTo(sourceState)
            .and(TMigrateRepoStorageTask::lastModifiedDate.name).isEqualTo(lastModifiedDate)
        val update = Update()
            .set(TMigrateRepoStorageTask::state.name, targetState)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
        return updateFirst(Query(criteria), update)
    }

    fun updateMigratedCount(id: String, count: Long): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val update = Update()
            .set(TMigrateRepoStorageTask::migratedCount.name, count)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
        return updateFirst(Query(criteria), update)
    }

    fun updateTotalCount(id: String, count: Long): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val update = Update()
            .set(TMigrateRepoStorageTask::totalCount.name, count)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
        return updateFirst(Query(criteria), update)
    }
}
