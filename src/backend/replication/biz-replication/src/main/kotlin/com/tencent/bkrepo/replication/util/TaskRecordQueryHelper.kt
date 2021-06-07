package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

/**
 * 执行日志查询条件构造工具
 */
object TaskRecordQueryHelper {

    fun detailListQuery(
        recordId: String,
        packageName: String? = null,
        repoName: String? = null,
        clusterName: String? = null
    ): Query {
        val criteria = where(TReplicaRecordDetail::recordId).isEqualTo(recordId)
            .apply {
                packageName?.let { and("packageConstraint.packageKey").regex("^$it") }
            }.apply {
                repoName?.let { and(TReplicaRecordDetail::localRepoName).isEqualTo(it) }
            }.apply {
                clusterName?.let { and(TReplicaRecordDetail::remoteCluster).isEqualTo(it) }
            }
        return Query(criteria)
            .with(Sort.by(Sort.Order(Sort.Direction.DESC, TReplicaRecordDetail::startTime.name)))
    }
}
