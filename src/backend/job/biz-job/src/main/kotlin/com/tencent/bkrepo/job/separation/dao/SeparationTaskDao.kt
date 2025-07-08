/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.SeparationContent
import com.tencent.bkrepo.job.separation.pojo.task.SeparationCount
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SeparationTaskDao : SimpleMongoDao<TSeparationTask>() {

    fun findTasksByRepo(projectId: String, repoName: String): List<TSeparationTask> {
        val criteria = Criteria().and(TSeparationTask::projectId.name).isEqualTo(projectId)
            .and(TSeparationTask::repoName.name).isEqualTo(repoName)
        return find(Query(criteria))
    }

    fun exist(
        projectId: String, repoName: String, state: String,
        content: SeparationContent? = null,
        type: String? = null,
        separationDate: LocalDateTime? = null,
        overwrite: Boolean? = null
    ): Boolean {
        val criteria = Criteria().and(TSeparationTask::projectId.name).isEqualTo(projectId)
            .and(TSeparationTask::repoName.name).isEqualTo(repoName)
            .and(TSeparationTask::state.name).ne(state)
        separationDate?.let {
            criteria.and(TSeparationTask::separationDate.name).isEqualTo(separationDate)
        }
        type?.let {
            criteria.and(TSeparationTask::type.name).isEqualTo(type)
        }
        content?.let {
            criteria.and(TSeparationTask::content.name).isEqualTo(content)
        }
        overwrite?.let {
            criteria.and(TSeparationTask::overwrite.name).isEqualTo(overwrite)
        }
        return exists(Query(criteria))
    }

    fun find(state: String?,
             projectId: String?,
             repoName: String?,
             taskType: String?,
             pageRequest: PageRequest
    ): List<TSeparationTask> {
        return find(buildQuery(state, projectId, repoName, taskType).with(pageRequest))
    }

    fun count(state: String?, projectId: String?, repoName: String?, taskType: String?): Long {
        return count(buildQuery(state, projectId, repoName, taskType))
    }

    private fun buildQuery(state: String?, projectId: String?, repoName: String?, taskType: String?): Query {
        val criteria = Criteria()
        state?.let { criteria.and(TSeparationTask::state.name).isEqualTo(it) }
        projectId?.let { criteria.and(TSeparationTask::projectId.name).isEqualTo(projectId) }
        repoName?.let { criteria.and(TSeparationTask::repoName.name).isEqualTo(repoName) }
        taskType?.let { criteria.and(TSeparationTask::type.name).isEqualTo(taskType) }
        return Query(criteria)
    }

    fun updateState(
        taskId: String,
        state: SeparationTaskState,
        count: SeparationCount? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ) {
        val criteria = Criteria().and(ID).isEqualTo(taskId)
        val update = Update.update(TSeparationTask::lastModifiedBy.name, SYSTEM_USER)
            .set(TSeparationTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSeparationTask::state.name, state.name)
        startDate?.let { update.set(TSeparationTask::startDate.name, startDate) }
        endDate?.let { update.set(TSeparationTask::endDate.name, endDate) }
        count?.let { update.set(TSeparationTask::totalCount.name, count) }
        this.updateFirst(Query(criteria), update)
    }
}
