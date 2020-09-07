package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import org.springframework.stereotype.Component

@Component
class NodeQueryInterpreter : MongoQueryInterpreter() {
    init {
        addModelInterceptor(NodeQueryInterceptor())
        addModelInterceptor(NodeSelectInterceptor())
        addRuleInterceptor(StageTagRuleInterceptor())
        addRuleInterceptor(MetadataRuleInterceptor())
    }
}
