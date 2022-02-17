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
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScanTaskDao : SimpleMongoDao<TScanTask>() {
    fun updateStatus(
        taskId: String,
        status: ScanTaskStatus
    ): UpdateResult {
        val query = buildQuery(taskId)
        val update = buildUpdate().set(TScanTask::status.name, status.name)
        return updateFirst(query, update)
    }

    /**
     * 将已提交所有子任务且都扫描完的任务设置为结束状态
     */
    fun taskFinished(
        taskId: String,
        status: ScanTaskStatus = ScanTaskStatus.FINISHED,
        finishedTime: LocalDateTime = LocalDateTime.now()
    ): UpdateResult {
        val criteria = TScanTask::id.isEqualTo(taskId)
            .and(TScanTask::status).isEqualTo(ScanTaskStatus.SCANNING_SUBMITTED)
            .and(TScanTask::scanning).isEqualTo(0L)
        val query = Query(criteria)
        val update = buildUpdate(finishedTime)
            .set(TScanTask::status.name, status)
            .set(TScanTask::finishedDateTime.name, finishedTime)
        return updateFirst(query, update)
    }

    fun updateScanningCount(taskId: String, count: Int): UpdateResult {
        val query = buildQuery(taskId)
        val update = buildUpdate()
            .inc(TScanTask::scanning.name, count)
            .inc(TScanTask::total.name, count)
        return updateFirst(query, update)
    }

    fun updateScanResult(
        taskId: String,
        count: Int,
        scanResultOverview: Map<String, Long>
    ): UpdateResult {
        val query = buildQuery(taskId)
        val update = buildUpdate()
            .inc(TScanTask::scanned.name, count)
            .inc(TScanTask::scanning.name, -count)
        scanResultOverview.forEach { (key, value) ->
            update.inc("${TScanTask::scanResultOverview.name}.$key", value)
        }

        return updateFirst(query, update)
    }

    private fun buildQuery(taskId: String) = Query(TScanTask::id.isEqualTo(taskId))

    private fun buildUpdate(lastModifiedDate: LocalDateTime = LocalDateTime.now()): Update =
        Update.update(TScanTask::lastModifiedDate.name, lastModifiedDate)
}
