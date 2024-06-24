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

package com.tencent.bkrepo.job.separation.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.task.SeparationCount
import com.tencent.bkrepo.job.separation.pojo.task.SeparationPointer
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SeparationTaskDao : SimpleMongoDao<TSeparationTask>() {

    fun findUndoTaskByProjectIdAndRepoName(projectId: String, repoName: String, type: String): List<TSeparationTask> {
        val criteria = where(TSeparationTask::projectId).isEqualTo(projectId)
            .and(TSeparationTask::repoName).isEqualTo(repoName)
            .and(TSeparationTask::type).isEqualTo(type)
            .and(TSeparationTask::state).isEqualTo(SeparationTaskState.PENDING.name)
        return this.find(Query(criteria))
    }

    fun updateState(taskId: String, state: SeparationTaskState) {
        val criteria = where(TSeparationTask::id).isEqualTo(taskId)
        val update = Update().set(TSeparationTask::lastModifiedBy.name, SYSTEM_USER)
            .set(TSeparationTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSeparationTask::state.name, state.name)
        this.updateFirst(Query(criteria), update)
    }

    fun updateContentAndStat(taskId: String, state: SeparationTaskState, count: SeparationCount, lastRunId: SeparationPointer? = null) {
        val criteria = where(TSeparationTask::id).isEqualTo(taskId)
        val update = Update().set(TSeparationTask::lastModifiedBy.name, SYSTEM_USER)
            .set(TSeparationTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSeparationTask::state.name, state.name)
            .set(TSeparationTask::totalCount.name, count)
            .set(TSeparationTask::lastRunId.name, lastRunId)
        this.updateFirst(Query(criteria), update)
    }
}
