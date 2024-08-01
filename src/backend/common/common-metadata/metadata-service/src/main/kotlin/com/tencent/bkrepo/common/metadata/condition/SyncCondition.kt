package com.tencent.bkrepo.common.metadata.condition

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class SyncCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        try {
            context.classLoader?.loadClass("com.mongodb.client.MongoClient")
            return true
        } catch (e: ClassNotFoundException) {
            return false
        }
    }
}
