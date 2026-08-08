package com.tencent.bkrepo.common.mongo.api.routing

import org.springframework.data.mongodb.core.MongoTemplate

data class DualWriteContext(
    val route: WriteRoute,
    val collectionName: String,
    val defaultTemplate: MongoTemplate,
    val ruleName: String? = route.ruleName,
    val enqueueOnFailure: () -> Unit = {},
)

interface DualWriteExecutor {
    fun <T> execute(
        context: DualWriteContext,
        primary: () -> T,
        secondary: (primaryResult: T) -> Unit,
    ): T
}
