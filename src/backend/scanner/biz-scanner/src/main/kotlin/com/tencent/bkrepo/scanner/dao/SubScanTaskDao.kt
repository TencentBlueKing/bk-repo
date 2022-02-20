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

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.SubScanTaskStatus
import org.springframework.data.mongodb.core.query.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubScanTaskDao : SimpleMongoDao<TSubScanTask>() {

    fun deleteById(subTaskId: String): DeleteResult {
        val query = Query(TSubScanTask::id.isEqualTo(subTaskId))
        return remove(query)
    }

    fun updateStatus(
        subTaskId: String,
        status: SubScanTaskStatus
    ): UpdateResult {
        val query = Query(TSubScanTask::id.isEqualTo(subTaskId))
        val update = Update()
            .set(TSubScanTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSubScanTask::status.name, status.name)
        return updateFirst(query, update)
    }

    /**
     * 获取一个待执行任务
     */
    fun firstCreatedOrEnqueuedTask(): TSubScanTask? {
        val query = Query(TSubScanTask::status.inValues(listOf(SubScanTaskStatus.CREATED, SubScanTaskStatus.ENQUEUED)))
        return findOne(query)
    }

    /**
     * 获取一个执行超时的任务
     *
     * @param timeoutSeconds 允许执行的最长时间
     */
    fun firstTimeoutTask(timeoutSeconds: Long): TSubScanTask? {
        val taskExecuteBeforeDate = LocalDateTime.now().minusSeconds(timeoutSeconds)
        val criteria = TSubScanTask::status.isEqualTo(SubScanTaskStatus.EXECUTING)
            .and(TSubScanTask::lastModifiedDate).lt(taskExecuteBeforeDate)
        return findOne(Query(criteria))
    }
}
