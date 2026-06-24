package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.routing.MongoRoutingContext

/** node 集合路由上下文，委托给通用 [MongoRoutingContext]，规则名固定为 "node"。 */
object NodeRoutingContext {
    private const val RULE = "node"

    fun set(projectId: String) = MongoRoutingContext.set(RULE, projectId)

    fun get(): String? = MongoRoutingContext.get(RULE)

    fun clear() = MongoRoutingContext.clear(RULE)

    fun <T> withProject(projectId: String, block: () -> T): T =
        MongoRoutingContext.withRoutingKey(RULE, projectId, block)
}
