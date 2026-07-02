package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.DiskUsageLevel
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class DiskUsageGuard(
    private val registry: MongoRoutingRegistry,
    private val defaultMongoTemplate: MongoTemplate,
) {

    fun levelForTemplate(template: MongoTemplate, label: String): DiskUsageResult {
        val percent = readDiskUsagePercent(template) ?: return DiskUsageResult(
            label, null, DiskUsageLevel.NORMAL, "dbStats fs size unavailable",
        )
        val level = when {
            percent >= BLOCK_MIGRATION_PCT -> DiskUsageLevel.BLOCK_MIGRATION
            percent >= BLOCK_WRITE_PCT -> DiskUsageLevel.BLOCK_WRITE
            percent >= WARN_PCT -> DiskUsageLevel.WARN
            else -> DiskUsageLevel.NORMAL
        }
        if (level != DiskUsageLevel.NORMAL) {
            logger.warn("Disk usage $label: $percent% → $level")
        }
        return DiskUsageResult(label, percent, level, null)
    }

    fun checkRule(ruleName: String): List<DiskUsageResult> {
        val results = mutableListOf(levelForTemplate(defaultMongoTemplate, "default"))
        registry.allPrimaryTemplates(ruleName).forEach { (instance, tmpl) ->
            results += levelForTemplate(tmpl, instance)
        }
        return results
    }

    fun blocksMigration(ruleName: String): Boolean =
        checkRule(ruleName).any { it.level == DiskUsageLevel.BLOCK_MIGRATION }

    fun blocksWrite(ruleName: String): Boolean =
        checkRule(ruleName).any {
            it.level == DiskUsageLevel.BLOCK_WRITE || it.level == DiskUsageLevel.BLOCK_MIGRATION
        }

    data class DiskUsageResult(
        val instanceLabel: String,
        val usagePercent: Int?,
        val level: DiskUsageLevel,
        val detail: String?,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DiskUsageGuard::class.java)
        private const val WARN_PCT = 70
        private const val BLOCK_WRITE_PCT = 80
        private const val BLOCK_MIGRATION_PCT = 85

        private fun readDiskUsagePercent(template: MongoTemplate): Int? {
            val stats = template.db.runCommand(Document("dbStats", 1))
            val total = (stats["fsTotalSize"] as? Number)?.toLong() ?: return null
            val used = (stats["fsUsedSize"] as? Number)?.toLong() ?: return null
            if (total <= 0L) return null
            return ((used.toDouble() / total) * 100).toInt()
        }
    }
}
