package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 同步失败记录数据访问层
 */
@Repository
class ReplicaFailureRecordDao : SimpleMongoDao<TReplicaFailureRecord>() {

    /**
     * 查找需要重试的记录（重试次数小于指定值且不在重试中）
     */
    fun findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes: Int): List<TReplicaFailureRecord> {
        val query = Query(
            where(TReplicaFailureRecord::retryCount).lte(maxRetryTimes)
                .and(TReplicaFailureRecord::retrying.name).isEqualTo(false)
        )
        return this.find(query)
    }

    fun updateRetryStatus(recordId: String, retrying: Boolean) {
        val query = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val update = Update.update(TReplicaFailureRecord::retrying.name, retrying)
            .set(TReplicaFailureRecord::lastModifiedDate.name, LocalDateTime.now())
        this.updateFirst(query, update)
    }

    fun deleteById(recordId: String) {
        this.removeById(recordId)
    }

    /**
     * 更新现有记录的失败原因和重试次数
     */
    fun updateExistingRecord(
        recordId: String,
        failureReason: String?,
        incrementRetryCount: Boolean = true
    ) {
        val query = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val update = Update()
            .set(TReplicaFailureRecord::lastModifiedDate.name, LocalDateTime.now())
            .set(TReplicaFailureRecord::retrying.name, false)

        if (failureReason != null) {
            update.set(TReplicaFailureRecord::failureReason.name, failureReason)
        }

        if (incrementRetryCount) {
            update.inc(TReplicaFailureRecord::retryCount.name, 1)
        }

        this.updateFirst(query, update)
    }

    /**
     * 删除过期记录
     */
    fun deleteExpiredRecords(maxRetryTimes: Int, expireBefore: LocalDateTime): Long {
        val query = Query(
            where(TReplicaFailureRecord::retryCount).gte(maxRetryTimes)
                .and(TReplicaFailureRecord::lastModifiedDate.name).lt(expireBefore)
        )
        return this.remove(query).deletedCount
    }

    /**
     * 根据条件构建查询
     */
    fun buildQuery(
        taskKey: String?,
        remoteClusterId: String?,
        projectId: String?,
        repoName: String?,
        remoteProjectId: String?,
        remoteRepoName: String?,
        failureType: ReplicaObjectType?,
        retrying: Boolean?,
        maxRetryCount: Int?,
        sortField: String?,
        sortDirection: Sort.Direction?
    ): Query {
        val criteria = Criteria()
        taskKey?.let { criteria.and(TReplicaFailureRecord::taskKey.name).`is`(it) }
        remoteClusterId?.let { criteria.and(TReplicaFailureRecord::remoteClusterId.name).`is`(it) }
        projectId?.let { criteria.and(TReplicaFailureRecord::projectId.name).`is`(it) }
        repoName?.let { criteria.and(TReplicaFailureRecord::repoName.name).`is`(it) }
        remoteProjectId?.let { criteria.and(TReplicaFailureRecord::remoteProjectId.name).`is`(it) }
        remoteRepoName?.let { criteria.and(TReplicaFailureRecord::remoteRepoName.name).`is`(it) }
        failureType?.let { criteria.and(TReplicaFailureRecord::failureType.name).`is`(it) }
        retrying?.let { criteria.and(TReplicaFailureRecord::retrying.name).`is`(it) }
        maxRetryCount?.let { criteria.and(TReplicaFailureRecord::retryCount.name).gt(it) }

        val query = Query(criteria)
        sortField?.let {
            val direction = sortDirection ?: Sort.Direction.DESC
            query.with(Sort.by(direction, it))
        }

        return query
    }


    /**
     * 根据条件批量删除记录
     */
    fun deleteByConditions(ids: List<String>?, maxRetryCount: Int?): Long {
        if (ids.isNullOrEmpty() && maxRetryCount == null) return 0
        val criteria = Criteria()
        ids?.takeIf { it.isNotEmpty() }?.let { criteria.and(ID).`in`(it) }
        maxRetryCount?.let { criteria.and(TReplicaFailureRecord::retryCount.name).gt(it) }
        return remove(Query(criteria)).deletedCount
    }

    /**
     * 记录分发失败
     * 如果存在相同记录，则更新失败原因和重试次数；否则创建新记录
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
        failedRecordId: String?
    ) {
        val existingRecord = if (failedRecordId.isNullOrEmpty()) {
            null
        } else {
            findById(failedRecordId)
        }
        if (existingRecord != null) {
            // 如果存在相同记录，则更新失败原因和重试次数
            updateExistingRecord(existingRecord.id!!, failureReason)
        } else {
            // 如果不存在相同记录，则创建新记录
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
            save(failureRecord)
            logger.info(
                "Created new failure record for task[$taskKey], type[$failureType] reason[$failureReason]"
            )
        }
    }

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: String, failureReason: String?) {
        updateExistingRecord(recordId, failureReason, incrementRetryCount = true)
        logger.info("Incremented retry count for record[$recordId]")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaFailureRecordDao::class.java)
    }
}
