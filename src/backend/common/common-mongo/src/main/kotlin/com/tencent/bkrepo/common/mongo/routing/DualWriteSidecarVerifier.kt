package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.model.ReconciliationLog
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

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
    private val blockNodeCollectionBase: String = NodeReconciliationHelper.DEFAULT_BLOCK_NODE_BASE,
) {

    private val lastResults = java.util.concurrent.ConcurrentHashMap<String, MutableList<VerificationResult>>()

    /**
     * 按需对账：由 M6 的 migration API 调用。
     * 全量 DUAL_WRITE 项目遍历，项目数少时无瓶颈；
     * 若未来项目数 > 100 可改为分页 + 单项目异步执行。
     */
    fun verify() {
        PROJECT_ROUTING_RULES.forEach(::verifyRule)
    }

    private fun verifyRule(ruleName: String) {
        val configured = registry.allConfiguredProjectsByInstance(ruleName)
        if (configured.isEmpty()) return
        configured.forEach { (instanceName, projects) ->
            val heavyTemplate = registry.primaryTemplateByInstance(ruleName, instanceName) ?: return@forEach
            projects.forEach { projectId ->
                verifySingle(ruleName, projectId, heavyTemplate)
            }
        }
    }

    /** 单项目按需对账，供 [verify] 及 M6 API 直接调用。 */
    fun verifySingle(ruleName: String, projectId: String, heavyTemplate: MongoTemplate) {
        verifyAndRecord(ruleName, projectId, heavyTemplate)
    }

    private fun verifyAndRecord(ruleName: String, projectId: String, heavyTemplate: MongoTemplate) {
        if (!registry.isProjectInDualWrite(ruleName, projectId)) return
        try {
            val result = verifyProject(ruleName, projectId, heavyTemplate)
            recordResult(ruleName, projectId, result)
            persistLog(
                ruleName = ruleName,
                projectId = projectId,
                checkType = SIDECAR_CHECK_TYPE,
                passed = result.passed,
                detail = "samples=${result.sampleCount} diffs=${result.diffCount}",
            )
            if (!result.passed) {
                logger.warn(
                    "Sidecar verification FAILED for rule[{}] project[{}]: {} diffs out of {} samples",
                    ruleName, projectId, result.diffCount, result.sampleCount,
                )
            } else {
                logger.debug(
                    "Sidecar verification PASSED for rule[{}] project[{}]: {} samples OK",
                    ruleName, projectId, result.sampleCount,
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Sidecar verification error for rule[{}] project[{}]: {}",
                ruleName, projectId, e.message,
            )
            recordResult(ruleName, projectId, VerificationResult(0, 0, 1, passed = false))
        }
    }

    fun isRecentVerificationPassed(
        ruleName: String,
        projectId: String,
        requiredPassRounds: Int = 3,
    ): Boolean {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where("projectId").`is`(projectId)
                    .and("checkType").`is`(SIDECAR_CHECK_TYPE),
                ruleNameCriteria(ruleName),
            ),
        )
            .with(Sort.by(Sort.Direction.DESC, "createdAt"))
            .limit(requiredPassRounds)
        val recent = defaultMongoTemplate.find(query, ReconciliationLog::class.java)
        return recent.size >= requiredPassRounds && recent.all { it.passed }
    }

    fun getHistory(ruleName: String, projectId: String): List<VerificationResult> =
        lastResults[historyKey(ruleName, projectId)]?.toList() ?: emptyList()

    private fun verifyProject(
        ruleName: String,
        projectId: String,
        heavyTemplate: MongoTemplate,
    ): VerificationResult {
        val collectionName = NodeReconciliationHelper.shardCollectionForRule(
            projectId = projectId,
            ruleName = ruleName,
            blockNodeCollectionBase = blockNodeCollectionBase,
            shardingCount = SHARDING_COUNT,
        )
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

    private fun recordResult(ruleName: String, projectId: String, result: VerificationResult) {
        routingMetrics?.recordReconciliationLastPassed(result.passed)
        lastResults.compute(historyKey(ruleName, projectId)) { _, list ->
            val history = list ?: mutableListOf()
            history.add(result)
            if (history.size > MAX_HISTORY) {
                history.subList(0, history.size - MAX_HISTORY).clear()
            }
            history
        }
    }

    private fun ruleNameCriteria(ruleName: String): Criteria =
        if (ruleName == NodeReconciliationHelper.NODE_RULE) {
            Criteria().orOperator(
                Criteria.where("ruleName").`is`(NodeReconciliationHelper.NODE_RULE),
                Criteria.where("ruleName").exists(false),
            )
        } else {
            Criteria.where("ruleName").`is`(ruleName)
        }

    private fun historyKey(ruleName: String, projectId: String): String = "$ruleName:$projectId"

    data class VerificationResult(
        val sampleCount: Int,
        val matchedCount: Int,
        val diffCount: Int,
        val passed: Boolean,
        val timestamp: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    )

    companion object {
        private val PROJECT_ROUTING_RULES = setOf(
            NodeReconciliationHelper.NODE_RULE,
            NodeReconciliationHelper.BLOCK_NODE_RULE,
        )
        private const val PROJECT_FIELD = "projectId"
        private const val SHARDING_COUNT = 256
        private const val SAMPLE_SIZE = 100
        private const val MAX_HISTORY = 20
        private const val SIDECAR_CHECK_TYPE = "SIDECAR"

        private val logger = LoggerFactory.getLogger(DualWriteSidecarVerifier::class.java)
    }

    private fun persistLog(
        ruleName: String,
        projectId: String,
        checkType: String,
        passed: Boolean,
        detail: String,
    ) {
        runCatching {
            defaultMongoTemplate.insert(
                ReconciliationLog(
                    projectId = projectId,
                    ruleName = ruleName,
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
