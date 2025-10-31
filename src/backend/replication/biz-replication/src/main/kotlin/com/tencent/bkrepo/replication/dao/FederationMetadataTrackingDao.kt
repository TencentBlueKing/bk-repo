package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class FederationMetadataTrackingDao : SimpleMongoDao<TFederationMetadataTracking>() {

    /**
     * 更新记录的重试次数和失败原因
     */
    fun updateRetryInfo(id: String, failureReason: String?) {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val query = Query(criteria)
        val update = Update()
            .set(TFederationMetadataTracking::failureReason.name, failureReason)
            .set(TFederationMetadataTracking::retrying.name, false)
            .set(TFederationMetadataTracking::lastModifiedDate.name, LocalDateTime.now())
        updateFirst(query, update)
    }

    /**
     * 设置重试状态
     */
    fun setRetrying(id: String, retrying: Boolean, incrementRetryCount: Boolean = false) {
        val criteria = Criteria.where(ID).isEqualTo(id)
        val query = Query(criteria)
        val update = Update()
            .set(TFederationMetadataTracking::retrying.name, retrying)
            .set(TFederationMetadataTracking::lastModifiedDate.name, LocalDateTime.now())
        if (incrementRetryCount) {
            update.inc(TFederationMetadataTracking::retryCount.name, 1)
        }
        updateFirst(query, update)
    }

    /**
     * 根据任务key和节点ID查询记录
     */
    fun findByTaskKeyAndNodeId(taskKey: String, nodeId: String): TFederationMetadataTracking? {
        val criteria = Criteria.where(TFederationMetadataTracking::taskKey.name).`is`(taskKey)
            .and(TFederationMetadataTracking::nodeId.name).`is`(nodeId)
        return findOne(Query(criteria))
    }

    /**
     * 删除指定任务key和节点ID的记录
     */
    fun deleteByTaskKeyAndNodeId(taskKey: String, nodeId: String) {
        val criteria = Criteria.where(TFederationMetadataTracking::taskKey.name).`is`(taskKey)
            .and(TFederationMetadataTracking::nodeId.name).`is`(nodeId)
        remove(Query(criteria))
    }

    /**
     * 删除过期的失败记录
     */
    fun deleteExpiredFailedRecords(maxRetryNum: Int, beforeDate: LocalDateTime): Long {
        val criteria = Criteria.where(TFederationMetadataTracking::retryCount.name).gt(maxRetryNum)
            .and(TFederationMetadataTracking::lastModifiedDate.name).lt(beforeDate)
        return remove(Query(criteria)).deletedCount
    }

    /**
     * 根据条件构建查询
     */
    fun buildQuery(
        taskKey: String?,
        remoteClusterId: String?,
        projectId: String?,
        localRepoName: String?,
        remoteProjectId: String?,
        remoteRepoName: String?,
        retrying: Boolean?,
        maxRetryCount: Int?,
        sortField: String?,
        sortDirection: Sort.Direction?
    ): Query {
        val criteria = Criteria()
        taskKey?.let { criteria.and(TFederationMetadataTracking::taskKey.name).`is`(it) }
        remoteClusterId?.let { criteria.and(TFederationMetadataTracking::remoteClusterId.name).`is`(it) }
        projectId?.let { criteria.and(TFederationMetadataTracking::projectId.name).`is`(it) }
        localRepoName?.let { criteria.and(TFederationMetadataTracking::localRepoName.name).`is`(it) }
        remoteProjectId?.let { criteria.and(TFederationMetadataTracking::remoteProjectId.name).`is`(it) }
        remoteRepoName?.let { criteria.and(TFederationMetadataTracking::remoteRepoName.name).`is`(it) }
        retrying?.let { criteria.and(TFederationMetadataTracking::retrying.name).`is`(it) }
        maxRetryCount?.let { criteria.and(TFederationMetadataTracking::retryCount.name).gt(it) }

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
        maxRetryCount?.let { criteria.and(TFederationMetadataTracking::retryCount.name).gt(it) }
        return remove(Query(criteria)).deletedCount
    }
}
