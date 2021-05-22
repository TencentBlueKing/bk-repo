package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo

/**
 * 任务查询条件构造工具
 */
object TaskQueryHelper {

    /**
     * 构造list查询条件
     */
    fun buildListQuery(
        name: String? = null,
        lastExecutionStatus: ExecutionStatus? = null,
        enabled: Boolean? = null
    ): Query {
        val criteria = Criteria()
        name?.takeIf { it.isNotBlank() }?.apply { criteria.and(TReplicaTask::name).regex("^$this") }
        lastExecutionStatus?.apply { criteria.and(TReplicaTask::lastExecutionStatus).isEqualTo("$this") }
        enabled?.apply { criteria.and(TReplicaTask::enabled).isEqualTo("$this") }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TReplicaTask::createdDate.name))
    }

    fun undoTaskQuery(): Query {
        val criteria = Criteria.where(TReplicaTask::replicaType.name).`is`(ReplicaType.SCHEDULED)
            .and(TReplicaTask::status.name).`in`(ReplicationStatus.UNDO_STATUS)
            .and(TReplicaTask::enabled.name).`is`(true)
        return Query(criteria)
    }
}
