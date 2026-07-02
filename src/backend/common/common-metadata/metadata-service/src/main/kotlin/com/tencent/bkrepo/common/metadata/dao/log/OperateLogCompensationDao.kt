package com.tencent.bkrepo.common.metadata.dao.log

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation
import com.tencent.bkrepo.common.metadata.model.TOperateLogCompensation.Companion.STATUS_PENDING
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class OperateLogCompensationDao : SimpleMongoDao<TOperateLogCompensation>() {

    fun findPending(limit: Int): List<TOperateLogCompensation> {
        val query = Query(Criteria.where(TOperateLogCompensation::status.name).`is`(STATUS_PENDING))
            .limit(limit)
        return find(query)
    }
}
