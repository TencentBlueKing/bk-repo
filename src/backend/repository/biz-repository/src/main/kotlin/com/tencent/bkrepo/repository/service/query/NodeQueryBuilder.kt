package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryBuilder
import org.springframework.stereotype.Component

@Component
class NodeQueryBuilder : MongoQueryBuilder() {
    init {
        addModelInterceptor(NodeQueryInterceptor())
        addModelInterceptor(NodeSelectInterceptor())
        addRuleInterceptor(MetadataRuleInterceptor())
    }
}
