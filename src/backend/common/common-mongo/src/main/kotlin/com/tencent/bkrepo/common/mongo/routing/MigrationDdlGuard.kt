package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/** M7：迁移期 DDL 冻结（G-26） */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MigrationDdlGuard(
    private val properties: MongoMultiInstanceProperties,
    private val registry: MongoRoutingRegistry,
) {

    fun assertDdlAllowed(collectionName: String, instanceLabel: String = DEFAULT_LABEL) {
        val ruleName = registry.resolveRuleName(collectionName) ?: return
        val rule = properties.rules[ruleName] ?: return
        val locks = rule.migration.projectLocks
        if (!locks.freezeDdl) return
        val blocked = locks.freezeDdlInstances
        if (blocked.isNotEmpty() && instanceLabel !in blocked) return
        throw IllegalStateException(
            "DDL blocked during migration: ensureIndex on $collectionName ($instanceLabel)",
        )
    }

    companion object {
        private const val DEFAULT_LABEL = "default"
    }
}
