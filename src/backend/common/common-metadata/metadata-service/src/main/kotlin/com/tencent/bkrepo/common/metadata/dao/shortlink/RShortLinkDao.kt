package com.tencent.bkrepo.common.metadata.dao.shortlink

import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TShortLink
import com.tencent.bkrepo.common.mongo.reactive.dao.SimpleMongoReactiveDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

/**
 * 短链接响应式数据访问层
 */
@Component
@Conditional(ReactiveCondition::class)
class RShortLinkDao : SimpleMongoReactiveDao<TShortLink>() {

    suspend fun findByCode(code: String): TShortLink? {
        return this.findOne(Query(TShortLink::code.isEqualTo(code)))
    }

    suspend fun deleteByCode(code: String): Boolean {
        return this.remove(Query(TShortLink::code.isEqualTo(code))).deletedCount > 0
    }
}
