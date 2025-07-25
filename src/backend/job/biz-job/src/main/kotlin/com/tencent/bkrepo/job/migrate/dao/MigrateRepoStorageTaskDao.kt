/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.Companion.EXECUTING_STATE
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDateTime

@Repository
class MigrateRepoStorageTaskDao : SimpleMongoDao<TMigrateRepoStorageTask>() {

    fun count(state: String?): Long {
        val criteria = Criteria()
        state?.let { criteria.and(TMigrateRepoStorageTask::state.name).isEqualTo(it) }
        return count(Query(criteria))
    }

    fun find(state: String?, pageRequest: PageRequest): List<TMigrateRepoStorageTask> {
        val criteria = Criteria()
        state?.let { criteria.and(TMigrateRepoStorageTask::state.name).isEqualTo(it) }
        return find(Query(criteria).with(pageRequest))
    }

    fun exists(projectId: String, repoName: String): Boolean {
        val query = Query(buildCriteria(projectId, repoName))
        val update = Update.update(TMigrateRepoStorageTask::projectId.name, projectId)
        return findFromPrimary(query, update) != null
    }

    fun migrating(projectId: String, repoName: String): Boolean {
        val criteria = buildCriteria(projectId, repoName)
            .and(TMigrateRepoStorageTask::startDate.name).ne(null)
        val update = Update.update(TMigrateRepoStorageTask::projectId.name, projectId)
        return findFromPrimary(Query(criteria), update) != null
    }

    fun find(projectId: String, repoName: String): TMigrateRepoStorageTask? {
        val query = Query(buildCriteria(projectId, repoName))
        val update = Update.update(TMigrateRepoStorageTask::projectId.name, projectId)
        return findFromPrimary(query, update)
    }

    fun updateStartDate(id: String, startDate: LocalDateTime): TMigrateRepoStorageTask? {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val update = Update()
            .set(TMigrateRepoStorageTask::startDate.name, startDate)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
        val options = FindAndModifyOptions().returnNew(true)
        return findAndModify(Query(criteria), update, options, TMigrateRepoStorageTask::class.java)
    }

    fun updateState(
        id: String,
        sourceState: String,
        targetState: String,
        lastModifiedDate: LocalDateTime,
        instanceId: String? = null,
    ): TMigrateRepoStorageTask? {
        val criteria = Criteria.where(ID).isEqualTo(id)
            .and(TMigrateRepoStorageTask::state.name).isEqualTo(sourceState)
            .and(TMigrateRepoStorageTask::lastModifiedDate.name).isEqualTo(lastModifiedDate)
        val update = Update()
            .set(TMigrateRepoStorageTask::state.name, targetState)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TMigrateRepoStorageTask::executingOn.name, instanceId)
        val options = FindAndModifyOptions().returnNew(true)
        return findAndModify(Query(criteria), update, options, TMigrateRepoStorageTask::class.java)
    }

    fun updateMigratedCount(id: String, count: Long, lastMigratedNodeId: String): UpdateResult {
        val criteria = Criteria
            .where(ID).isEqualTo(id)
            .and(TMigrateRepoStorageTask::migratedCount.name).lt(count)
        val update = Update()
            .set(TMigrateRepoStorageTask::migratedCount.name, count)
            .set(TMigrateRepoStorageTask::lastMigratedNodeId.name, lastMigratedNodeId)
        return updateFirst(Query(criteria), update)
    }

    fun updateTotalCount(id: String, count: Long): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val update = Update().set(TMigrateRepoStorageTask::totalCount.name, count)
        return updateFirst(Query(criteria), update)
    }

    fun executableTask(state: String): TMigrateRepoStorageTask? {
        val criteria = TMigrateRepoStorageTask::state.isEqualTo(state)
        val update = Update.update(TMigrateRepoStorageTask::state.name, state)
        val options = FindAndModifyOptions().returnNew(true)
        return findAndModify(Query(criteria), update, options, TMigrateRepoStorageTask::class.java)
    }

    /**
     * 获取在本实例中执行超时的任务列表
     */
    fun timeoutTasks(instanceId: String, timeout: Duration): List<TMigrateRepoStorageTask> {
        val before = LocalDateTime.now().minus(timeout)
        val criteria = TMigrateRepoStorageTask::state.inValues(EXECUTING_STATE)
            .and(TMigrateRepoStorageTask::executingOn.name).isEqualTo(instanceId)
            .and(TMigrateRepoStorageTask::lastModifiedDate.name).lt(before)
        return find(Query(criteria))
    }

    fun correctableTask(interval: Duration): TMigrateRepoStorageTask? {
        // 需要等待一段时间，待所有传输中的制品传输结束后再执行correct
        val beforeDateTime = LocalDateTime.now().minus(interval)
        val criteria = TMigrateRepoStorageTask::state.isEqualTo(MIGRATE_FINISHED.name)
            .and(TMigrateRepoStorageTask::startDate.name).lte(beforeDateTime)

        val update = Update.update(TMigrateRepoStorageTask::state.name, MIGRATE_FINISHED.name)
        return findFromPrimary(Query(criteria), update)
    }

    private fun findFromPrimary(query: Query, update: Update): TMigrateRepoStorageTask? {
        val options = FindAndModifyOptions().returnNew(true)
        // 使用findAndModify强制从主库查询
        return findAndModify(query, update, options, TMigrateRepoStorageTask::class.java)
    }

    private fun buildCriteria(projectId: String, repoName: String) =
        TMigrateRepoStorageTask::projectId.isEqualTo(projectId)
            .and(TMigrateRepoStorageTask::repoName.name).isEqualTo(repoName)
}
