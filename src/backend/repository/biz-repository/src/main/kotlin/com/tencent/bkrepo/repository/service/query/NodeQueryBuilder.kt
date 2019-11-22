package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryBuilder
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
@Component
class NodeQueryBuilder : MongoQueryBuilder() {
    init {
        addModelInterceptor(NodeQueryInterceptor())
        addModelInterceptor(NodeSelectInterceptor())
        addRuleInterceptor(MetadataRuleInterceptor())
    }
}
