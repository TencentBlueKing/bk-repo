package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.NodeReconciliationHelper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 定期对账（§3.17.4 第三层）：checksum / 分段 count / 全量 _id 扫描。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class NodePeriodicReconciliationJob(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
) {

    private val lastSegmentedDay = ConcurrentHashMap<String, Long>()
    private val lastFullScanWeek = ConcurrentHashMap<String, Long>()

    @Scheduled(fixedDelay = RECONCILE_INTERVAL_MS)
    fun reconcile() {
        val projectsByInstance = registry.allConfiguredProjectsByInstance(NODE_RULE)
        if (projectsByInstance.isEmpty()) return
        projectsByInstance.forEach { (instanceName, projects) ->
            val heavyTemplate = registry.primaryTemplateByInstance(NODE_RULE, instanceName) ?: return@forEach
            projects.forEach { projectId ->
                if (!shouldReconcile(projectId)) return@forEach
                reconcileProject(projectId, heavyTemplate)
            }
        }
    }

    private fun shouldReconcile(projectId: String): Boolean {
        return registry.isProjectInDualWrite(NODE_RULE, projectId) ||
            registry.isProjectRoutedOut(NODE_RULE, projectId)
    }

    private fun reconcileProject(projectId: String, heavyTemplate: MongoTemplate) {
        val isDualWrite = registry.isProjectInDualWrite(NODE_RULE, projectId)
        val shardCol = NodeReconciliationHelper.shardCollection(projectId)
        val details = mutableListOf<String>()

        val countMismatches = NodeReconciliationHelper.countMismatches(
            projectId, defaultMongoTemplate, heavyTemplate,
        )
        if (countMismatches.isNotEmpty()) {
            countMismatches.forEach {
                details += "count:${it.collectionName} d=${it.defaultCount} h=${it.heavyCount}"
                logger.warn(
                    "Periodic reconcile count mismatch: project={}, collection={}, default={}, heavy={}",
                    projectId, it.collectionName, it.defaultCount, it.heavyCount,
                )
            }
            if (isDualWrite) {
                NodeReconciliationHelper.repairSecondaryFromPrimary(
                    projectId,
                    heavyTemplate,
                    defaultMongoTemplate,
                    countMismatches.map { it.shardIdx },
                )
                details += "repaired shards ${countMismatches.map { it.shardIdx }}"
            }
        }

        val dChecksum = NodeReconciliationHelper.checksumSnapshot(
            defaultMongoTemplate, shardCol, projectId,
        )
        val hChecksum = NodeReconciliationHelper.checksumSnapshot(
            heavyTemplate, shardCol, projectId,
        )
        if (!NodeReconciliationHelper.checksumsEqual(dChecksum, hChecksum)) {
            details += "checksum mismatch"
            logger.error(
                "Periodic reconcile checksum mismatch: project={}, default={}, heavy={}",
                projectId, dChecksum, hChecksum,
            )
        }

        val today = LocalDate.now().toEpochDay()
        if (lastSegmentedDay[projectId] != today) {
            val segMismatches = NodeReconciliationHelper.segmentedCountMismatches(
                projectId, defaultMongoTemplate, heavyTemplate,
            )
            if (segMismatches.isNotEmpty()) {
                details += "segmented count: $segMismatches"
                logger.error(
                    "Periodic reconcile segmented count mismatch: project={}, segments={}",
                    projectId, segMismatches,
                )
            }
            lastSegmentedDay[projectId] = today
        }

        val week = LocalDate.now().toEpochDay() / 7
        if (lastFullScanWeek[projectId] != week) {
            val (missingInHeavy, extraInHeavy) = NodeReconciliationHelper.fullIdScanDiff(
                projectId, heavyTemplate, defaultMongoTemplate,
            )
            if (missingInHeavy.isNotEmpty() || extraInHeavy.isNotEmpty()) {
                details += "id scan missing=${missingInHeavy.size} extra=${extraInHeavy.size}"
                logger.error(
                    "Periodic reconcile full _id scan diff: project={}, missing={}, extra={}",
                    projectId, missingInHeavy.size, extraInHeavy.size,
                )
            }
            lastFullScanWeek[projectId] = week
        }

        val passed = details.isEmpty()
        if (passed) {
            logger.debug("Periodic reconcile passed for project[{}]", projectId)
        }
        NodeReconciliationHelper.persistLog(
            defaultMongoTemplate,
            projectId,
            "PERIODIC",
            passed,
            if (passed) "ok" else details.joinToString("; "),
        )
    }

    companion object {
        private const val NODE_RULE = "node"
        private const val RECONCILE_INTERVAL_MS = 3_600_000L
        private val logger = LoggerFactory.getLogger(NodePeriodicReconciliationJob::class.java)
    }
}
