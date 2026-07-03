package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.DualWriteContext
import com.tencent.bkrepo.common.mongo.api.routing.DualWriteExecutor
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.stereotype.Component

/** M2 [DualWriteExecutor] 实现，委托 [MongoDualWriteSupport]。 */
@Component
class DefaultDualWriteExecutor(
    private val registry: MongoRoutingRegistry?,
) : DualWriteExecutor {

    override fun <T> execute(
        context: DualWriteContext,
        primary: () -> T,
        secondary: (primaryResult: T) -> Unit,
    ): T {
        val result = MongoDualWriteSupport.executePrimaryWrite(
            route = context.route,
            collectionName = context.collectionName,
            defaultTemplate = context.defaultTemplate,
            registry = registry,
        ) { primary() }
        MongoDualWriteSupport.submitSecondaryWrite(
            route = context.route,
            collectionName = context.collectionName,
            enqueue = context.enqueueOnFailure,
            action = { secondary(result) },
        )
        return result
    }
}
