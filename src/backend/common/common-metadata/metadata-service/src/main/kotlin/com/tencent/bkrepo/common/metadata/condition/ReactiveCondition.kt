package com.tencent.bkrepo.common.metadata.condition

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class ReactiveCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        try {
            context.classLoader?.loadClass("com.mongodb.reactivestreams.client.MongoClient")
            return true
        } catch (e: ClassNotFoundException) {
            return false
        }
    }
}
