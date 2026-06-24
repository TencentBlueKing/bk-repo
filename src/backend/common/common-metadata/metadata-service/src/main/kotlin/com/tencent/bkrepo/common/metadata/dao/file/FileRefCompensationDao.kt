package com.tencent.bkrepo.common.metadata.dao.file

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation.Companion.STATUS_PENDING
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
@Conditional(SyncCondition::class)
class FileRefCompensationDao : SimpleMongoDao<TFileRefCompensation>() {

    fun findPending(limit: Int): List<TFileRefCompensation> {
        val query = Query(Criteria.where(TFileRefCompensation::status.name).`is`(STATUS_PENDING))
            .limit(limit)
        return find(query)
    }
}
