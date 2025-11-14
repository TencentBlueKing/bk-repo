package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 失败记录仓储类
 * 统一管理所有失败记录的数据库操作，包括创建、更新、查询等
 */
@Component
class FailureRecordRepository(
    private val replicaFailureRecordDao: ReplicaFailureRecordDao
) {

    /**
     * 记录分发失败
     */
    fun recordFailure(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        failureType: ReplicaObjectType,
        packageConstraint: PackageConstraint?,
        pathConstraint: PathConstraint?,
        failureReason: String?,
        event: ArtifactEvent?,
        failedRecordId: String?,
    ) {
        val existingRecord = if (failedRecordId.isNullOrEmpty()) {
            null
        } else {
            findByRecordId(failedRecordId)
        }
        if (existingRecord != null) {
            // 如果存在相同记录，则更新失败原因和重试次数
            updateExisting(existingRecord, failureReason)
        } else {
            // 如果不存在相同记录，则创建新记录
            create(
                taskKey = taskKey,
                remoteClusterId = remoteClusterId,
                projectId = projectId,
                localRepoName = localRepoName,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                failureType = failureType,
                packageConstraint = packageConstraint,
                pathConstraint = pathConstraint,
                failureReason = failureReason,
                event = event
            )
        }
    }

    /**
     * 创建新的失败记录
     */
    fun create(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        failureType: ReplicaObjectType,
        packageConstraint: PackageConstraint?,
        pathConstraint: PathConstraint?,
        failureReason: String?,
        event: ArtifactEvent?
    ) {
        val failureRecord = TReplicaFailureRecord(
            taskKey = taskKey,
            projectId = projectId,
            repoName = localRepoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            failureType = failureType,
            packageConstraint = packageConstraint,
            pathConstraint = pathConstraint,
            failureReason = failureReason,
            event = event,
            retryCount = 0,
            retrying = false,
            remoteClusterId = remoteClusterId
        )
        replicaFailureRecordDao.save(failureRecord)
        logger.info(
            "Created new failure record for task[$taskKey], type[$failureType] reason[$failureReason]"
        )
    }

    /**
     * 根据ID查找记录
     */
    fun findById(id: String): TReplicaFailureRecord? {
        return replicaFailureRecordDao.findById(id)
    }

    /**
     * 根据ID查找记录（别名方法，保持向后兼容）
     */
    fun findByRecordId(id: String): TReplicaFailureRecord? {
        return findById(id)
    }

    /**
     * 更新现有记录
     */
    fun updateExisting(existingRecord: TReplicaFailureRecord, failureReason: String?) {
        replicaFailureRecordDao.updateExistingRecord(existingRecord.id!!, failureReason)
    }

    /**
     * 更新重试状态
     */
    fun updateRetryStatus(recordId: String, retrying: Boolean) {
        replicaFailureRecordDao.updateRetryStatus(recordId, retrying)
        logger.info("Updated retry status for record[$recordId] to $retrying")
    }

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: String, failureReason: String?) {
        replicaFailureRecordDao.updateExistingRecord(recordId, failureReason, incrementRetryCount = true)
        logger.info("Incremented retry count for record[$recordId]")
    }

    /**
     * 删除记录
     */
    fun deleteById(recordId: String) {
        replicaFailureRecordDao.deleteById(recordId)
        logger.info("Deleted failure record[$recordId]")
    }

    /**
     * 构建查询对象
     */
    fun buildQuery(option: ReplicaFailureRecordListOption, sortDirection: Sort.Direction) =
        replicaFailureRecordDao.buildQuery(
            taskKey = option.taskKey,
            remoteClusterId = option.remoteClusterId,
            projectId = option.projectId,
            repoName = option.repoName,
            remoteProjectId = option.remoteProjectId,
            remoteRepoName = option.remoteRepoName,
            failureType = option.failureType,
            retrying = option.retrying,
            maxRetryCount = option.maxRetryCount,
            sortField = option.sortField,
            sortDirection = sortDirection
        )

    /**
     * 验证删除条件
     */
    fun validateDeleteConditions(request: ReplicaFailureRecordDeleteRequest): Boolean {
        return !request.ids.isNullOrEmpty() || request.maxRetryCount != null
    }

    /**
     * 查找需要重试的记录（重试次数小于指定值且不在重试中）
     */
    fun findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes: Int): List<TReplicaFailureRecord> {
        return replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes)
    }

    /**
     * 删除过期记录
     */
    fun deleteExpiredRecords(maxRetryTimes: Int, expireBefore: LocalDateTime): Long {
        val deletedCount = replicaFailureRecordDao.deleteExpiredRecords(maxRetryTimes, expireBefore)
        logger.info("Deleted $deletedCount expired records before $expireBefore with maxRetryTimes >= $maxRetryTimes")
        return deletedCount
    }

    /**
     * 统计查询结果数量
     */
    fun count(query: Query): Long {
        return replicaFailureRecordDao.count(query)
    }

    /**
     * 执行查询
     */
    fun find(query: Query): List<TReplicaFailureRecord> {
        return replicaFailureRecordDao.find(query)
    }

    /**
     * 根据条件批量删除记录
     */
    fun deleteByConditions(ids: List<String>?, maxRetryCount: Int?): Long {
        val deletedCount = replicaFailureRecordDao.deleteByConditions(ids, maxRetryCount)
        logger.info("Deleted $deletedCount records by conditions: ids=${ids?.size ?: 0}, maxRetryCount=$maxRetryCount")
        return deletedCount
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FailureRecordRepository::class.java)
    }
}

