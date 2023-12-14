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

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_DISPATCHER
import com.tencent.bkrepo.analyst.pojo.request.CredentialsKeyFiles
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.EXECUTING
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.PULLED
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.elemMatch
import org.springframework.data.mongodb.core.query.exists
import org.springframework.data.mongodb.core.query.gte
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubScanTaskDao(
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerProperties: ScannerProperties,
) : AbsSubScanTaskDao<TSubScanTask>() {

    fun findByCredentialsKeyAndSha256List(credentialsKeyFiles: List<CredentialsKeyFiles>): List<TSubScanTask> {
        val criteria = Criteria()
        credentialsKeyFiles.forEach {
            criteria.orOperator(
                Criteria
                    .where(TSubScanTask::credentialsKey.name).isEqualTo(it.credentialsKey)
                    .and(TSubScanTask::sha256.name).`in`(it.sha256List)
            )
        }
        return find(Query(criteria))
    }

    fun findByCredentialsAndSha256(credentialsKey: String?, sha256: String): TSubScanTask? {
        val query = Query(
            TSubScanTask::credentialsKey.isEqualTo(credentialsKey).and(TSubScanTask::sha256.name).isEqualTo(sha256)
        )
        return findOne(query)
    }

    fun findByParentId(parentTaskId: String): List<TSubScanTask> {
        val query = Query(
            TSubScanTask::parentScanTaskId.isEqualTo(parentTaskId)
        )
        return find(query)
    }

    fun deleteById(subTaskId: String): DeleteResult {
        val query = Query(Criteria.where(ID).isEqualTo(subTaskId))
        return remove(query)
    }

    /**
     * 更新任务状态
     *
     * @param subTaskId 待更新的子任务id
     * @param status 更新后的任务状态
     * @param oldStatus 更新前的任务状态，只有旧状态匹配时才会更新
     * @param lastModifiedDate 最后更新时间，用于充当乐观锁，只有最后修改时间匹配时候才更新
     * @param timeoutDateTime 扫描执行超时时间点
     *
     * @return 更新结果
     */
    fun updateStatus(
        subTaskId: String,
        status: SubScanTaskStatus,
        oldStatus: SubScanTaskStatus? = null,
        lastModifiedDate: LocalDateTime? = null,
        timeoutDateTime: LocalDateTime? = null
    ): UpdateResult {
        val now = LocalDateTime.now()
        val criteria = Criteria.where(ID).isEqualTo(subTaskId)

        oldStatus?.let { criteria.and(TSubScanTask::status.name).isEqualTo(it.name) }
        lastModifiedDate?.let { criteria.and(TSubScanTask::lastModifiedDate.name).isEqualTo(it) }

        val query = Query(criteria)
        val update = Update()
            .set(TSubScanTask::lastModifiedDate.name, now)
            .set(TSubScanTask::status.name, status.name)
        if (status == EXECUTING) {
            update.set(TSubScanTask::startDateTime.name, now)
        } else if (status == PULLED) {
            update.set(TSubScanTask::heartbeatDateTime.name, now)
            update.set(TSubScanTask::timeoutDateTime.name, timeoutDateTime)
            update.inc(TSubScanTask::executedTimes.name, 1)
        }
        if (status != PULLED && status != EXECUTING) {
            update.unset(TSubScanTask::timeoutDateTime.name)
            update.unset(TSubScanTask::heartbeatDateTime.name)
        }

        val updateResult = updateFirst(query, update)
        if (updateResult.modifiedCount == 1L) {
            logger.debug(
                "update status success, subTaskId[$subTaskId], newStatus[$status]," +
                        " oldStatus[$oldStatus], lastModifiedDate[$lastModifiedDate], newModifiedDate[$now]"
            )
            planArtifactLatestSubScanTaskDao.updateStatus(subTaskId, status.name, now = now)
        }

        return updateResult
    }

    fun heartbeat(subtaskId: String): UpdateResult {
        val criteria = Criteria.where(ID).isEqualTo(subtaskId).and(TSubScanTask::status.name).`in`(PULLED, EXECUTING)
        val update = Update.update(TSubScanTask::heartbeatDateTime.name, LocalDateTime.now())
        return updateFirst(Query(criteria), update)
    }

    fun countStatus(status: SubScanTaskStatus): Long {
        return count(Query(TSubScanTask::status.isEqualTo(status.name)))
    }

    /**
     * 唤醒[projectId]一个子任务为可执行状态
     */
    fun notify(projectId: String, count: Int = 1): UpdateResult? {
        if (count <= 0) {
            return null
        }

        val criteria = Criteria
            .where(TSubScanTask::projectId.name).isEqualTo(projectId)
            .and(TSubScanTask::status.name).isEqualTo(SubScanTaskStatus.BLOCKED.name)
        val query = Query(criteria)
            .with(Sort.by(TSubScanTask::createdDate.name))
            .limit(count)
        val subtaskIds = find(query).map { it.id!! }

        if (subtaskIds.isEmpty()) {
            return null
        }

        criteria.and(ID).inValues(subtaskIds)
        val update = Update()
            .set(TSubScanTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSubScanTask::status.name, SubScanTaskStatus.CREATED.name)

        logger.info("notify subtasks$subtaskIds of project[$projectId]")
        return updateMulti(Query(criteria), update)
    }

    /**
     * 获取项目[projectId]扫描中的任务数量
     */
    fun scanningCount(projectId: String, includeGlobal: Boolean = false): Long {
        val criteria = Criteria
            .where(TSubScanTask::projectId.name).isEqualTo(projectId)
            .and(TSubScanTask::status.name).inValues(SubScanTaskStatus.RUNNING_STATUS)
        if (!includeGlobal) {
            criteria.and("${TSubScanTask::metadata.name}.key").ne(TaskMetadata.TASK_METADATA_GLOBAL)
        }
        return count(Query(criteria))
    }

    /**
     * 获取项目[projectId]扫描中的任务
     */
    fun findScanning(projectId: String): List<TSubScanTask> {
        val criteria = Criteria
            .where(TSubScanTask::projectId.name).isEqualTo(projectId)
            .and(TSubScanTask::status.name).inValues(SubScanTaskStatus.RUNNING_STATUS)
        return find(Query(criteria))
    }

    fun updateStatus(
        subTaskIds: List<String>,
        status: SubScanTaskStatus
    ): UpdateResult {
        val query = Query(Criteria.where(ID).`in`(subTaskIds))
        val update = Update()
            .set(TSubScanTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSubScanTask::status.name, status.name)
        val updateResult = updateMulti(query, update)
        planArtifactLatestSubScanTaskDao.updateStatus(subTaskIds, status.name)
        archiveSubScanTaskDao.updateStatus(subTaskIds, status.name)
        return updateResult
    }

    fun firstTaskByStatusIn(status: List<String>?, dispatcher: String?): TSubScanTask? {
        val query = buildStatusAndDispatcherQuery(status, dispatcher).with(Sort.by(TSubScanTask::createdDate.name))
        return findOne(query)
    }

    /**
     * 获取指定状态任务数量
     *
     * @param status 要查询的任务状态列表
     * @param dispatcher 任务使用的分发器
     *
     * @return 任务数量
     */
    fun countTaskByStatusIn(status: List<String>?, dispatcher: String?): Long {
        val query = buildStatusAndDispatcherQuery(status, dispatcher)
        return count(query)
    }

    /**
     * 获取一个执行超时的任务
     *
     * @param dispatcher 指定分发器的超时任务，为null时表示默认执行器
     * @param allDispatcher 为true时查找所有分发器的超时任务
     *
     * @return 第一个超时任务
     */
    fun timeoutTasks(dispatcher: String? = null, allDispatcher: Boolean = true): Page<TSubScanTask> {
        val criteriaList = mutableListOf(
            buildTimeoutCriteria(),
            TSubScanTask::status.inValues(PULLED.name, EXECUTING.name),
        )
        if (!allDispatcher) {
            criteriaList.add(dispatcherCriteria(dispatcher))
        }
        val criteria = Criteria().andOperator(criteriaList)

        return page(Query(criteria), Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE))
    }

    /**
     * 获取处于阻塞状态超时的任务
     */
    fun blockedTimeoutTasks(timeoutSeconds: Long): Page<TSubScanTask> {
        val now = LocalDateTime.now()
        val criteria = Criteria
            .where(TSubScanTask::lastModifiedDate.name).lt(now.minusSeconds(timeoutSeconds))
            .and(TSubScanTask::status.name).isEqualTo(SubScanTaskStatus.BLOCKED.name)
        return page(Query(criteria), Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE))
    }

    private fun buildTimeoutCriteria(): Criteria {
        val heartbeatTimeoutSeconds = scannerProperties.heartbeatTimeout.seconds
        val maxTaskTimeoutSeconds = scannerProperties.maxTaskDuration.seconds
        val now = LocalDateTime.now()
        val timeoutCriteria = ArrayList<Criteria>()
        timeoutCriteria.add(TSubScanTask::timeoutDateTime.lt(now))
        if (heartbeatTimeoutSeconds > 0) {
            val heartbeatTimeoutCriteria = Criteria
                .where(TSubScanTask::heartbeatDateTime.name).lt(now.minusSeconds(heartbeatTimeoutSeconds))
            timeoutCriteria.add(heartbeatTimeoutCriteria)
        }

        if (maxTaskTimeoutSeconds > 0) {
            val maxTaskTimeoutCriteria =
                Criteria.where(TSubScanTask::createdDate.name).lt(now.minusSeconds(maxTaskTimeoutSeconds))
            timeoutCriteria.add(maxTaskTimeoutCriteria)
        }
        return Criteria().orOperator(timeoutCriteria)
    }

    private fun buildNotTimeoutCriteria(): List<Criteria> {
        val heartbeatTimeoutSeconds = scannerProperties.heartbeatTimeout.seconds
        val maxTaskTimeoutSeconds = scannerProperties.maxTaskDuration.seconds
        val now = LocalDateTime.now()
        val timeoutCriteria = ArrayList<Criteria>()

        // timeoutDateTime
        timeoutCriteria.add(
            Criteria().orOperator(
                TSubScanTask::timeoutDateTime.gte(now),
                TSubScanTask::timeoutDateTime.exists(false)
            )
        )

        // heartbeatDateTime
        if (heartbeatTimeoutSeconds > 0) {
            val heartbeatTimeoutCriteria = Criteria().orOperator(
                TSubScanTask::heartbeatDateTime.gte(now.minusSeconds(heartbeatTimeoutSeconds)),
                TSubScanTask::heartbeatDateTime.exists(false)
            )
            timeoutCriteria.add(heartbeatTimeoutCriteria)
        }

        // maxTaskTimeout
        if (maxTaskTimeoutSeconds > 0) {
            val maxTaskTimeoutCriteria =
                Criteria.where(TSubScanTask::createdDate.name).gte(now.minusSeconds(maxTaskTimeoutSeconds))
            timeoutCriteria.add(maxTaskTimeoutCriteria)
        }
        return timeoutCriteria
    }

    private fun buildStatusAndDispatcherQuery(status: List<String>?, dispatcher: String?): Query {
        val criteria = ArrayList<Criteria>(2)
        criteria.add(dispatcherCriteria(dispatcher))
        if (!status.isNullOrEmpty()) {
            criteria.add(TSubScanTask::status.inValues(status))
        }
        criteria.addAll(buildNotTimeoutCriteria())
        return Query(Criteria().andOperator(criteria))
    }

    private fun dispatcherCriteria(dispatcher: String?): Criteria {
        return if (dispatcher == null) {
            Criteria("${TSubScanTask::metadata.name}.${TaskMetadata::key.name}").ne(TASK_METADATA_DISPATCHER)
        } else {
            TSubScanTask::metadata.elemMatch(
                TaskMetadata::key.isEqualTo(TASK_METADATA_DISPATCHER)
                    .and(TaskMetadata::value.name).isEqualTo(dispatcher)
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubScanTaskDao::class.java)
    }
}
