package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
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

    fun incrementRetryCount(recordId: String, failureReason: String?) {
        val query = Query.query(Criteria.where(ID).isEqualTo(recordId))
        val update = Update().inc(TReplicaFailureRecord::retryCount.name, 1)
            .set(TReplicaFailureRecord::retrying.name, false)
            .set(TReplicaFailureRecord::lastModifiedDate.name, LocalDateTime.now())
        if (failureReason != null) {
            update.set(TReplicaFailureRecord::failureReason.name, failureReason)
        }
        this.updateFirst(query, update)
    }

    fun deleteById(recordId: String) {
        this.removeById(recordId)
    }

    /**
     * 查找是否存在相同的失败记录
     * 判断条件：taskKey + remoteClusterId + projectId + localRepoName + failureType + 约束条件
     */
    fun findExistingRecord(
        taskKey: String,
        projectId: String,
        remoteClusterId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        failureType: ReplicaObjectType,
        packageKey: String? = null,
        packageVersion: String? = null,
        fullPath: String? = null
    ): TReplicaFailureRecord? {
        val query = Query(
            where(TReplicaFailureRecord::taskKey).isEqualTo(taskKey)
                .and(TReplicaFailureRecord::remoteClusterId.name).isEqualTo(remoteClusterId)
                .and(TReplicaFailureRecord::projectId.name).isEqualTo(projectId)
                .and(TReplicaFailureRecord::repoName.name).isEqualTo(localRepoName)
                .and(TReplicaFailureRecord::failureType.name).isEqualTo(failureType)
                .and(TReplicaFailureRecord::packageKey.name).isEqualTo(packageKey)
                .and(TReplicaFailureRecord::packageVersion.name).isEqualTo(packageVersion)
                .and(TReplicaFailureRecord::fullPath.name).isEqualTo(fullPath)
        )

        return this.findOne(query)
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
}
