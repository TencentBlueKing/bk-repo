/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.replication.dao.ReplicaRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaRecordDetailDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaOverview
import com.tencent.bkrepo.replication.pojo.record.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailListOption
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordListOption
import com.tencent.bkrepo.replication.pojo.record.ReplicaTaskRecordInfo
import com.tencent.bkrepo.replication.pojo.record.request.RecordDetailInitialRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.replica.base.process.ProgressListener
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.util.CronUtils
import com.tencent.bkrepo.replication.util.TaskRecordQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(DefaultCondition::class)
class ReplicaRecordServiceImpl(
    private val replicaRecordDao: ReplicaRecordDao,
    private val replicaRecordDetailDao: ReplicaRecordDetailDao,
    private val replicaTaskDao: ReplicaTaskDao,
    private val clusterProperties: ClusterProperties,
    private val progressListener: ProgressListener
) : ReplicaRecordService {

    override fun startNewRecord(key: String): ReplicaRecordInfo {
        val initialRecord = initialRecord(key)
        val tReplicaTask = replicaTaskDao.findByKey(key)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
        tReplicaTask.status = ReplicaStatus.REPLICATING
        tReplicaTask.lastExecutionTime = LocalDateTime.now()
        if (isCronJob(tReplicaTask.setting, tReplicaTask.replicaType)) {
            tReplicaTask.nextExecutionTime =
                CronUtils.getNextTriggerTime(tReplicaTask.setting.executionPlan.cronExpression.orEmpty())
        }
        tReplicaTask.lastExecutionStatus = ExecutionStatus.RUNNING
        replicaTaskDao.save(tReplicaTask)
        return initialRecord
    }

    override fun initialRecord(taskKey: String): ReplicaRecordInfo {
        val record = TReplicaRecord(
            taskKey = taskKey,
            status = ExecutionStatus.RUNNING,
            startTime = LocalDateTime.now()
        )
        return try {
            replicaRecordDao.insert(record).let { convert(it)!! }
        } catch (exception: DuplicateKeyException) {
            logger.warn("init record [$taskKey] error: [${exception.message}]")
            throw exception
        }
    }

    override fun completeRecord(
        recordId: String,
        status: ExecutionStatus,
        errorReason: String?,
        replicaOverview: ReplicaOverview?
    ) {
        val replicaRecordInfo = getRecordById(recordId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, recordId)
        val record = with(replicaRecordInfo) {
            TReplicaRecord(
                id = id,
                taskKey = taskKey,
                status = status,
                startTime = startTime,
                endTime = LocalDateTime.now(),
                errorReason = errorReason,
                replicaOverview = replicaOverview
            )
        }
        val tReplicaTask = replicaTaskDao.findByKey(record.taskKey)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, record.taskKey)
        tReplicaTask.lastExecutionStatus = status
        tReplicaTask.status = if (isCronJob(tReplicaTask.setting, tReplicaTask.replicaType))
            ReplicaStatus.WAITING else ReplicaStatus.COMPLETED
        if (status == ExecutionStatus.SUCCESS) {
            tReplicaTask.replicatedBytes = tReplicaTask.totalBytes
        }
        progressListener.onFinish(tReplicaTask.id!!)
        replicaRecordDao.save(record)
        replicaTaskDao.save(tReplicaTask)
        logger.info("complete record [$recordId], status from [${replicaRecordInfo.status}] to [$status].")
    }

    override fun initialRecordDetail(request: RecordDetailInitialRequest): ReplicaRecordDetail {
        with(request) {
            val recordDetail = TReplicaRecordDetail(
                recordId = recordId,
                localCluster = clusterProperties.center.name.orEmpty(),
                remoteCluster = remoteCluster,
                localRepoName = localRepoName,
                repoType = repoType,
                packageConstraint = packageConstraint,
                pathConstraint = pathConstraint,
                artifactName = artifactName,
                version = version,
                conflictStrategy = conflictStrategy,
                size = size,
                sha256 = sha256,
                status = ExecutionStatus.RUNNING,
                progress = ReplicaProgress(),
                startTime = LocalDateTime.now(),
                executeType = executeType
            )
            return try {
                replicaRecordDetailDao.insert(recordDetail).let { convert(it)!! }
            } catch (exception: DuplicateKeyException) {
                logger.warn("init record detail [$recordId] error: [${exception.message}]")
                throw exception
            }
        }
    }

    override fun updateRecordDetailProgress(detailId: String, progress: ReplicaProgress) {
        val replicaRecordDetail = findRecordDetailById(detailId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, detailId)
        replicaRecordDetail.progress.success = progress.success
        replicaRecordDetail.progress.skip = progress.skip
        replicaRecordDetail.progress.failed = progress.failed
        replicaRecordDetail.progress.totalSize = progress.totalSize
        replicaRecordDetailDao.save(replicaRecordDetail)
        logger.info("Update record detail [$detailId] success.")
    }

    override fun completeRecordDetail(detailId: String, result: ExecutionResult) {
        val tReplicaRecordDetail = replicaRecordDetailDao.findById(detailId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, detailId)
        val recordDetail = tReplicaRecordDetail.copy(
            status = result.status,
            progress = result.progress!!,
            errorReason = result.errorReason,
            endTime = LocalDateTime.now()
        )
        replicaRecordDetailDao.save(recordDetail)
    }

    override fun listRecordsByTaskKey(key: String): List<ReplicaRecordInfo> {
        return replicaRecordDao.listByTaskKey(key).map { convert(it)!! }
    }

    override fun listRecordsPage(key: String, option: ReplicaRecordListOption): Page<ReplicaRecordInfo> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val query = TaskRecordQueryHelper.recordListQuery(key, option)
        val totalRecords = replicaRecordDao.count(query)
        val records = replicaRecordDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun listDetailsByRecordId(recordId: String): List<ReplicaRecordDetail> {
        return replicaRecordDetailDao.listByRecordId(recordId).map { convert(it)!! }
    }

    override fun getRecordAndTaskInfoByRecordId(recordId: String): ReplicaTaskRecordInfo {
        val replicaRecordInfo = getRecordById(recordId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, recordId)
        val taskKey = replicaRecordInfo.taskKey
        val replicaTask = replicaTaskDao.findByKey(taskKey)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, taskKey)
        return ReplicaTaskRecordInfo(replicaTask.replicaObjectType, replicaTask.replicaType, replicaRecordInfo)
    }

    override fun getRecordById(id: String): ReplicaRecordInfo? {
        return convert(replicaRecordDao.findById(id))
    }

    override fun getRecordDetailById(id: String): ReplicaRecordDetail? {
        return convert(findRecordDetailById(id))
    }

    override fun deleteRecordDetailById(id: String) {
        replicaRecordDetailDao.removeById(id)
    }

    private fun findRecordDetailById(id: String): TReplicaRecordDetail? {
        return replicaRecordDetailDao.findById(id)
    }

    override fun deleteByTaskKey(key: String) {
        replicaRecordDao.listByTaskKey(key).forEach {
            replicaRecordDetailDao.deleteByRecordId(it.id!!)
        }
        replicaRecordDao.deleteByTaskKey(key)
    }

    override fun listRecordDetailPage(
        recordId: String,
        option: ReplicaRecordDetailListOption
    ): Page<ReplicaRecordDetail> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val query = TaskRecordQueryHelper.recordDetailListQuery(recordId, option)
        val totalRecords = replicaRecordDetailDao.count(query)
        val records = replicaRecordDetailDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun findOrCreateLatestRecord(key: String): ReplicaRecordInfo {
        return findLatestRecord(key) ?: initialRecord(key)
    }

    override fun findLatestRecord(key: String): ReplicaRecordInfo? {
        val replicaRecordList = replicaRecordDao.listByTaskKey(key).map { convert(it) }
        if (replicaRecordList.isNotEmpty()) return replicaRecordList.first()!!
        return null
    }

    override fun writeBack(replicaRecordInfo: ReplicaRecordInfo) {
        return
    }

    override fun updateRecordReplicaOverview(recordId: String, replicaOverview: ReplicaOverview) {
        replicaRecordDao.updateFirst(
            Query(Criteria.where(ID).`is`(recordId)),
            Update.update(TReplicaRecord::replicaOverview.name, replicaOverview)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaRecordServiceImpl::class.java)

        fun isCronJob(setting: ReplicaSetting, replicaType: ReplicaType): Boolean {
            return !setting.executionPlan.cronExpression.isNullOrBlank() &&
                replicaType == ReplicaType.SCHEDULED
        }

        private fun convert(tReplicaRecord: TReplicaRecord?): ReplicaRecordInfo? {
            return tReplicaRecord?.let {
                ReplicaRecordInfo(
                    id = it.id!!,
                    taskKey = it.taskKey,
                    status = it.status,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    errorReason = it.errorReason,
                    replicaOverview = it.replicaOverview
                )
            }
        }

        private fun convert(tReplicaRecordDetail: TReplicaRecordDetail?): ReplicaRecordDetail? {
            return tReplicaRecordDetail?.let {
                ReplicaRecordDetail(
                    id = it.id.orEmpty(),
                    recordId = it.recordId,
                    localCluster = it.localCluster,
                    remoteCluster = it.remoteCluster,
                    localRepoName = it.localRepoName,
                    repoType = it.repoType,
                    packageConstraint = it.packageConstraint,
                    pathConstraint = it.pathConstraint,
                    artifactName = it.artifactName,
                    version = it.version,
                    conflictStrategy = it.conflictStrategy,
                    size = it.size,
                    sha256 = it.sha256,
                    status = it.status,
                    progress = it.progress,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    errorReason = it.errorReason,
                    executeType = it.executeType
                )
            }
        }
    }
}
