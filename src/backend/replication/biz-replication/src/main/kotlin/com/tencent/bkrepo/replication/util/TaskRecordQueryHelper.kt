package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailListOption
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

/**
 * 执行日志查询条件构造工具
 */
object TaskRecordQueryHelper {

    fun recordDetailListQuery(
        recordId: String,
        option: ReplicaRecordDetailListOption
    ): Query {
        with(option) {
            val criteria = where(TReplicaRecordDetail::recordId).isEqualTo(recordId)
                .apply {
                    packageName?.let { and("packageConstraint.packageKey").regex("^$it") }
                }.apply {
                    repoName?.let { and(TReplicaRecordDetail::localRepoName).isEqualTo(it) }
                }.apply {
                    clusterName?.let { and(TReplicaRecordDetail::remoteCluster).isEqualTo(it) }
                }.apply {
                    path?.let { and("pathConstraint.path").isEqualTo("^$it") }
                }.apply {
                    status?.let { and(TReplicaRecordDetail::status).isEqualTo(it) }
                }
            return Query(criteria)
                .with(Sort.by(Sort.Order(Sort.Direction.DESC, TReplicaRecordDetail::startTime.name)))
        }
    }

    fun recordListQuery(key: String): Query {
        val criteria = where(TReplicaRecord::taskKey).isEqualTo(key)
        return Query(criteria)
            .with(Sort.by(Sort.Order(Sort.Direction.DESC, TReplicaRecord::startTime.name)))
    }
}
