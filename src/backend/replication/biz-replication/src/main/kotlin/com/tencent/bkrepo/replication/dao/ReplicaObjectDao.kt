package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TReplicaObject
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * 同步任务数据访问层
 */
@Repository
class ReplicaObjectDao : SimpleMongoDao<TReplicaObject>() {

    /**
     * 根据[taskKey]查找任务
     */
    fun findByTaskKey(taskKey: String): List<TReplicaObject> {
        return this.find(Query(TReplicaObject::taskKey.isEqualTo(taskKey)))
    }
}
