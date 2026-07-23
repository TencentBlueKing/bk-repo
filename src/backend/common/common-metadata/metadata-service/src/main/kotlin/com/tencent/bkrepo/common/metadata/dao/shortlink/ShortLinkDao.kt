package com.tencent.bkrepo.common.metadata.dao.shortlink

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TShortLink
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * 短链接数据访问层
 */
@Repository
@Conditional(SyncCondition::class)
class ShortLinkDao : SimpleMongoDao<TShortLink>() {

    fun findByCode(code: String): TShortLink? {
        return this.findOne(Query(TShortLink::code.isEqualTo(code)))
    }

    fun deleteByCode(code: String): Boolean {
        return this.remove(Query(TShortLink::code.isEqualTo(code))).deletedCount > 0
    }
}
