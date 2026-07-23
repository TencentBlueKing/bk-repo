package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.model.ReconciliationLog
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

/**
 * 双写旁路对账器（§25.3.2 E-05）。
 *
 * 按需对 DUAL_WRITE 项目随机抽样对比 Heavy vs Default 数据一致性。
 * 由 M6 的 migration API 在切流前显式调用（[verify] / [verifySingle]），
 * 而非定时触发，避免大数据量场景下无差别全量扫描带来的不可控压力。
 *
 * 切流要求：最近 3 轮对账零差异 + 补偿队列清零。
 */
class DualWriteSidecarVerifier(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val routingMetrics: MongoRoutingMetrics? = null,
) {

    private val lastResults = java.util.concurrent.ConcurrentHashMap<String, MutableList<VerificationResult>>()

    /**
     * 按需对账：由 M6 的 migration API 调用。
     * 全量 DUAL_WRITE 项目遍历，项目数少时无瓶颈；
     * 若未来项目数 > 100 可改为分页 + 单项目异步执行。
     */
    fun verify() {
        val rule = registry.allConfiguredProjectsByInstance(NODE_RULE)
        if (rule.isEmpty()) return

        rule.forEach { (instanceName, projects) ->
            val heavyTemplate = registry.primaryTemplateByInstance(NODE_RULE, instanceName) ?: return@forEach
            projects.forEach { projectId ->
                verifySingle(projectId, heavyTemplate)
            }
        }
    }

    /**
     * 单项目按需对账，供 [verify] 及 M6 API 直接调用。
     */
    fun verifySingle(projectId: String, heavyTemplate: MongoTemplate) {
        verifyAndRecord(projectId, heavyTemplate)
    }

    private fun verifyAndRecord(projectId: String, heavyTemplate: MongoTemplate) {
        if (!isDualWriteProject(projectId)) return
        try {
            val result = verifyProject(projectId, heavyTemplate)
            recordResult(projectId, result)
            persistLog(
                projectId,
                "SIDECAR",
                result.passed,
                "samples=${result.sampleCount} diffs=${result.diffCount}",
            )
            if (!result.passed) {
                logger.warn(
                    "Sidecar verification FAILED for project[{}]: {} diffs out of {} samples",
                    projectId, result.diffCount, result.sampleCount,
                )
            } else {
                logger.debug(
                    "Sidecar verification PASSED for project[{}]: {} samples OK",
                    projectId, result.sampleCount,
                )
            }
        } catch (e: Exception) {
            logger.error("Sidecar verification error for project[{}]: {}", projectId, e.message)
            recordResult(projectId, VerificationResult(0, 0, 1, passed = false))
        }
    }

    fun isRecentVerificationPassed(projectId: String, requiredPassRounds: Int = 3): Boolean {
        val results = lastResults[projectId] ?: return false
        val recent = results.takeLast(requiredPassRounds)
        return recent.size >= requiredPassRounds && recent.all { it.passed }
    }

    fun getHistory(projectId: String): List<VerificationResult> =
        lastResults[projectId]?.toList() ?: emptyList()

    private fun isDualWriteProject(projectId: String): Boolean =
        registry.isProjectInDualWrite(NODE_RULE, projectId)

    private fun verifyProject(projectId: String, heavyTemplate: MongoTemplate): VerificationResult {
        val collectionName = NodeReconciliationHelper.shardCollection(projectId, SHARDING_COUNT)
        val matchFilter = Filters.eq(PROJECT_FIELD, projectId)
        val defaultCount = defaultMongoTemplate.getCollection(collectionName)
            .countDocuments(matchFilter)
        if (defaultCount == 0L) return VerificationResult(0, 0, 0, passed = true)

        val sampleSize = minOf(SAMPLE_SIZE, defaultCount.toInt())
        val defaultDocs = defaultMongoTemplate.getCollection(collectionName)
            .aggregate(
                listOf(
                    Aggregates.match(matchFilter),
                    Aggregates.sample(sampleSize),
                ),
            )
            .map { Document(it) }
            .toList()

        var diffCount = 0
        for (defaultDoc in defaultDocs) {
            val id = defaultDoc.getObjectId("_id") ?: continue
            val heavyDoc = heavyTemplate.findById(id, Document::class.java, collectionName)
            if (heavyDoc == null || !NodeReconciliationHelper.documentsEqual(defaultDoc, heavyDoc)) {
                diffCount++
            }
        }

        return VerificationResult(
            sampleCount = defaultDocs.size,
            matchedCount = defaultDocs.size - diffCount,
            diffCount = diffCount,
            passed = diffCount == 0,
        )
    }

    private fun recordResult(projectId: String, result: VerificationResult) {
        routingMetrics?.recordReconciliationLastPassed(result.passed)
        lastResults.compute(projectId) { _, list ->
            val history = list ?: mutableListOf()
            history.add(result)
            if (history.size > MAX_HISTORY) {
                history.subList(0, history.size - MAX_HISTORY).clear()
            }
            history
        }
    }

    data class VerificationResult(
        val sampleCount: Int,
        val matchedCount: Int,
        val diffCount: Int,
        val passed: Boolean,
        val timestamp: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    )

    companion object {
        private const val NODE_RULE = "node"
        private const val PROJECT_FIELD = "projectId"
        private const val SHARDING_COUNT = 256
        private const val SAMPLE_SIZE = 100
        private const val MAX_HISTORY = 20

        private val logger = LoggerFactory.getLogger(DualWriteSidecarVerifier::class.java)
    }

    private fun persistLog(
        projectId: String,
        checkType: String,
        passed: Boolean,
        detail: String,
    ) {
        runCatching {
            defaultMongoTemplate.insert(
                ReconciliationLog(
                    projectId = projectId,
                    checkType = checkType,
                    passed = passed,
                    detail = detail,
                ),
            )
        }.onFailure {
            logger.warn("Failed to persist reconciliation log: {}", it.message)
        }
    }
}