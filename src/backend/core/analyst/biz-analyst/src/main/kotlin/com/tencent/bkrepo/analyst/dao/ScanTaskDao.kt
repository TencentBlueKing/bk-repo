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

package com.tencent.bkrepo.analyst.dao

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.ScanTaskQuery
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.ofTimestamp
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.elemMatch
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScanTaskDao : ScannerSimpleMongoDao<TScanTask>() {
    fun updateStatus(
        taskId: String,
        status: ScanTaskStatus,
        lastModifiedDate: LocalDateTime? = null
    ): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(taskId)
        lastModifiedDate?.let { criteria.and(TScanTask::lastModifiedDate.name).isEqualTo(it) }
        val update = buildUpdate().set(TScanTask::status.name, status.name)
        return updateFirst(Query(criteria), update)
    }

    /**
     * 将已提交所有子任务且都扫描完的任务设置为结束状态
     *
     * @param taskId 扫描结束的任务id
     * @param finishedTime 扫描结束时间
     * @param startDateTime 扫描开始时间，没有执行扫描子任务就结束的任务需要设置该参数
     */
    fun taskFinished(
        taskId: String,
        finishedTime: LocalDateTime = LocalDateTime.now(),
        startDateTime: LocalDateTime? = null
    ): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(taskId)
            .and(TScanTask::status).isEqualTo(ScanTaskStatus.SCANNING_SUBMITTED)
            .and(TScanTask::scanning).isEqualTo(0L)
        val query = Query(criteria)
        val update = buildUpdate(finishedTime)
            .set(TScanTask::status.name, ScanTaskStatus.FINISHED.name)
            .set(TScanTask::finishedDateTime.name, finishedTime)
        startDateTime?.let { update.set(TScanTask::startDateTime.name, startDateTime) }
        return updateFirst(query, update)
    }

    fun timeoutTask(timeoutSeconds: Long): TScanTask? {
        val beforeDate = LocalDateTime.now().minusSeconds(timeoutSeconds)
        val criteria = Criteria
            .where(TScanTask::status.name).`in`(ScanTaskStatus.PENDING.name, ScanTaskStatus.SCANNING_SUBMITTING.name)
            .and(TScanTask::lastModifiedDate.name).lt(beforeDate)
        return findOne(Query(criteria))
    }

    /**
     * 重置扫描任务状态
     *
     * @param taskId 扫描任务ID
     * @param lastModifiedDate 扫描任务最后修改时间
     *
     * @return 更新结果
     */
    fun resetTask(taskId: String, lastModifiedDate: LocalDateTime): TScanTask? {
        val query = Query(
            Criteria.where(ID).isEqualTo(taskId).and(TScanTask::lastModifiedDate.name).isEqualTo(lastModifiedDate)
        )
        val update = buildUpdate()
            .set(TScanTask::startDateTime.name, null)
            .set(TScanTask::finishedDateTime.name, null)
            .set(TScanTask::scanResultOverview.name, emptyMap<String, Long>())
            .set(TScanTask::status.name, ScanTaskStatus.PENDING.name)
            .set(TScanTask::total.name, 0L)
            .set(TScanTask::scanning.name, 0L)
            .set(TScanTask::failed.name, 0L)
            .set(TScanTask::scanned.name, 0L)
            .set(TScanTask::passed.name, 0L)

        val options = FindAndModifyOptions().returnNew(true)
        return determineMongoTemplate().findAndModify(query, update, options, TScanTask::class.java)
    }

    fun updateStartedDateTimeIfNotExists(taskId: String, startDateTime: LocalDateTime): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(taskId).and(TScanTask::startDateTime.name).isEqualTo(null)
        val update = buildUpdate().set(TScanTask::startDateTime.name, startDateTime)
        return updateFirst(Query(criteria), update)
    }

    fun updateScanningCount(taskId: String, count: Int): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(taskId)
            .and(TScanTask::status.name).isEqualTo(ScanTaskStatus.SCANNING_SUBMITTING)
        val update = buildUpdate()
            .inc(TScanTask::scanning.name, count)
            .inc(TScanTask::total.name, count)
        return updateFirst(Query(criteria), update)
    }

    /**
     * 更新扫描结果
     *
     * @param taskId 扫描任务id
     * @param count 更新的结果数量,
     * @param scanResultOverview 需要更新的预览结果
     * @param success 是否更新扫描成功的任务数量
     * @param reuseResult 是否是重用扫描结果的情况
     * @param passCount 通过质量红线的数量
     *
     */
    fun updateScanResult(
        taskId: String,
        count: Int,
        scanResultOverview: Map<String, Any?>,
        success: Boolean = true,
        reuseResult: Boolean = false,
        passCount: Long = 0L
    ): UpdateResult {
        val query = buildQuery(taskId)
        val update = buildUpdate()
        if (reuseResult) {
            update.inc(TScanTask::total.name, count)
        } else {
            // 不是重用扫描结果的情况才需要减去扫描中的任务数量
            update.inc(TScanTask::scanning.name, -count)
        }
        if (success) {
            update.inc(TScanTask::scanned.name, count)
        } else {
            update.inc(TScanTask::failed.name, count)
        }
        if (passCount != 0L) {
            update.inc(TScanTask::passed.name, count)
        }
        scanResultOverview.forEach { (key, value) ->
            if (value is Number) {
                update.inc("${TScanTask::scanResultOverview.name}.$key", value)
            }
        }

        return updateFirst(query, update)
    }

    fun findByIds(ids: List<String>): List<TScanTask> {
        return find(Query(Criteria.where(ID).inValues(ids)))
    }

    fun findByProjectIdAndId(projectId: String?, id: String): TScanTask? {
        val criteria = projectId?.let { TScanTask::projectId.isEqualTo(it).and(ID).isEqualTo(id) }
            ?: Criteria().and(ID).isEqualTo(id).and(TScanTask::metadata.name).elemMatch(buildGlobalTaskCriteria())
        return findOne(Query(criteria))
    }

    fun findUnFinished(projectId: String, planId: String): List<TScanTask> {
        return find(
            Query(
                TScanTask::projectId.isEqualTo(projectId).and(TScanTask::planId).isEqualTo(planId)
                    .and(TScanTask::status).`in`(ScanTaskStatus.unFinishedStatus)
            )
        )
    }

    fun find(scanTaskQuery: ScanTaskQuery, pageLimit: PageLimit): Page<TScanTask> {
        val criteria = Criteria()
        with(scanTaskQuery) {
            projectId?.let { criteria.and(TScanTask::projectId.name).isEqualTo(projectId) }
                ?: criteria.and(TScanTask::metadata.name).elemMatch(buildGlobalTaskCriteria())
            namePrefix?.let { criteria.and(TScanTask::name.name).regex("^$it") }
            planId?.let { criteria.and(TScanTask::planId.name).isEqualTo(it) }
            triggerType?.let { criteria.and(TScanTask::triggerType.name).isEqualTo(it) }
            after?.let { criteria.and(TScanTask::createdDate.name).gt(ofTimestamp(it)) }
            before?.let { criteria.and(TScanTask::createdDate.name).lt(ofTimestamp(it)) }
            scanner?.let { criteria.and(TScanTask::scanner.name).isEqualTo(it) }
            scannerType?.let { criteria.and(TScanTask::scannerType.name).isEqualTo(it) }
            status?.let { criteria.and(TScanTask::status.name).isEqualTo(it) }
        }
        val query = Query(criteria)
        val count = count(query)
        val pageRequest = Pages.ofRequest(pageLimit.pageNumber, pageLimit.pageSize)
        val sort = Sort.by(Sort.Direction.DESC, TScanTask::lastModifiedDate.name)
        query.with(pageRequest).with(sort)
        val tasks = find(query)
        return Pages.ofResponse(pageRequest, count, tasks)
    }

    fun existsByPlanIdAndStatus(planId: String, status: List<String>): Boolean {
        val criteria = Criteria
            .where(TScanTask::planId.name).isEqualTo(planId)
            .and(TScanTask::status.name).inValues(status)
        return exists(Query(criteria))
    }

    fun countStatus(status: ScanTaskStatus): Long {
        return count(Query(TScanTask::status.isEqualTo(status.name)))
    }

    fun countGlobalTask(status: List<ScanTaskStatus>, includeSystemTrigger: Boolean = false): Long {
        val criteria = TScanTask::metadata.elemMatch(buildGlobalTaskCriteria())
            .and(TScanTask::status.name).inValues(status)
        if (!includeSystemTrigger) {
            criteria.and(TScanTask::triggerType.name).ne(ScanTriggerType.ON_NEW_ARTIFACT_SYSTEM.name)
        }
        return count(Query(criteria))
    }

    private fun buildGlobalTaskCriteria() =
        TaskMetadata::key.isEqualTo(TaskMetadata.TASK_METADATA_GLOBAL).and(TaskMetadata::value.name).isEqualTo("true")

    private fun buildQuery(taskId: String) = Query(Criteria.where(ID).isEqualTo(taskId))

    private fun buildUpdate(lastModifiedDate: LocalDateTime = LocalDateTime.now()): Update =
        Update.update(TScanTask::lastModifiedDate.name, lastModifiedDate)
}
