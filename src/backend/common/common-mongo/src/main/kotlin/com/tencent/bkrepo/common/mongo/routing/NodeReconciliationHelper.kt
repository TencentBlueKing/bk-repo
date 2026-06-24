package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import org.bson.BsonTimestamp
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * node 迁移/双写对账共享逻辑（§3.17 / §1.4.4 VERIFY 门禁）。
 */
object NodeReconciliationHelper {

    data class CountMismatch(
        val shardIdx: Int,
        val collectionName: String,
        val defaultCount: Long,
        val heavyCount: Long,
    )

    data class ChecksumSnapshot(
        val count: Long,
        val deletedCount: Long,
        val maxLastModifiedEpochMillis: Long?,
    )

    data class VerifyGateResult(
        val passed: Boolean,
        val reason: String? = null,
        val mismatchedShards: List<Int> = emptyList(),
    )

    fun shardCollection(projectId: String, shardingCount: Int = SHARDING_COUNT): String {
        val idx = HashShardingUtils.shardingSequenceFor(projectId, shardingCount)
        return "$NODE_COLLECTION_PREFIX$idx"
    }

    fun projectCriteria(projectId: String): Criteria =
        Criteria.where(PROJECT_FIELD).`is`(projectId)

    fun countMismatches(
        projectId: String,
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        shardingCount: Int = SHARDING_COUNT,
    ): List<CountMismatch> {
        val mismatches = mutableListOf<CountMismatch>()
        for (shardIdx in 0 until shardingCount) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            val criteria = projectCriteria(projectId)
            val defaultCount = defaultTemplate.count(Query(criteria), col)
            val heavyCount = heavyTemplate.count(Query(criteria), col)
            if (defaultCount != heavyCount) {
                mismatches += CountMismatch(shardIdx, col, defaultCount, heavyCount)
            }
        }
        return mismatches
    }

    /** 每分片最新 N 条 _id 集合比对（保留原有 VERIFY 策略）。 */
    fun latestIdSetMismatches(
        projectId: String,
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        sampleSize: Int = LATEST_SAMPLE_SIZE,
        shardingCount: Int = SHARDING_COUNT,
    ): List<Int> {
        val mismatched = mutableListOf<Int>()
        for (shardIdx in 0 until shardingCount) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            val criteria = projectCriteria(projectId)
            val sampleQuery = Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, ID))
                .limit(sampleSize)
            val srcIds = defaultTemplate.find(sampleQuery, Document::class.java, col)
                .mapNotNull { it.getObjectId(ID) }.toSet()
            val dstIds = heavyTemplate.find(sampleQuery, Document::class.java, col)
                .mapNotNull { it.getObjectId(ID) }.toSet()
            if (srcIds != dstIds) mismatched += shardIdx
        }
        return mismatched
    }

    /**
     * §1.4.4：按 _id 升序分段，每段随机抽样对比内容。
     * 仅对项目所在分表执行（projectId 哈希单表）。
     */
    fun segmentedSampleMismatch(
        projectId: String,
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        segmentCount: Int = SEGMENT_COUNT,
        samplesPerSegment: Int = SAMPLES_PER_SEGMENT,
    ): Boolean {
        val col = shardCollection(projectId)
        val criteria = projectCriteria(projectId)
        val bounds = defaultTemplate.find(
            Query(criteria).with(Sort.by(Sort.Direction.ASC, ID)).limit(1),
            Document::class.java,
            col,
        ).firstOrNull()?.getObjectId(ID) to defaultTemplate.find(
            Query(criteria).with(Sort.by(Sort.Direction.DESC, ID)).limit(1),
            Document::class.java,
            col,
        ).firstOrNull()?.getObjectId(ID)
        val (minId, maxId) = bounds
        if (minId == null || maxId == null) return false

        val minTs = minId.timestamp.toLong() and 0xFFFFFFFFL
        val maxTs = maxId.timestamp.toLong() and 0xFFFFFFFFL
        if (minTs >= maxTs) {
            return sampleDocsMismatch(defaultTemplate, heavyTemplate, col, criteria, samplesPerSegment)
        }
        val step = (maxTs - minTs) / segmentCount.coerceAtLeast(1)
        for (seg in 0 until segmentCount) {
            val segStart = ObjectId((minTs + seg * step).toInt(), 0)
            val segEnd = if (seg == segmentCount - 1) {
                maxId
            } else {
                ObjectId((minTs + (seg + 1) * step).toInt(), 0)
            }
            val segCriteria = Criteria().andOperator(
                criteria,
                Criteria.where(ID).gte(segStart).lt(segEnd),
            )
            if (sampleDocsMismatch(defaultTemplate, heavyTemplate, col, segCriteria, samplesPerSegment)) {
                return true
            }
        }
        return false
    }

    /** §3.17.4.2：_id 范围分段 count，返回不一致的段标签。 */
    fun segmentedCountMismatches(
        projectId: String,
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        segmentCount: Int = SEGMENT_COUNT,
    ): List<String> {
        val col = shardCollection(projectId)
        val criteria = projectCriteria(projectId)
        val minId = defaultTemplate.find(
            Query(criteria).with(Sort.by(Sort.Direction.ASC, ID)).limit(1),
            Document::class.java,
            col,
        ).firstOrNull()?.getObjectId(ID) ?: return emptyList()
        val maxId = defaultTemplate.find(
            Query(criteria).with(Sort.by(Sort.Direction.DESC, ID)).limit(1),
            Document::class.java,
            col,
        ).firstOrNull()?.getObjectId(ID) ?: return emptyList()

        val minTs = minId.timestamp.toLong() and 0xFFFFFFFFL
        val maxTs = maxId.timestamp.toLong() and 0xFFFFFFFFL
        if (minTs >= maxTs) return emptyList()

        val step = (maxTs - minTs) / segmentCount.coerceAtLeast(1)
        val mismatches = mutableListOf<String>()
        for (seg in 0 until segmentCount) {
            val segStart = ObjectId((minTs + seg * step).toInt(), 0)
            val segEnd = if (seg == segmentCount - 1) {
                maxId
            } else {
                ObjectId((minTs + (seg + 1) * step).toInt(), 0)
            }
            val segCriteria = Criteria().andOperator(
                criteria,
                Criteria.where(ID).gte(segStart).lt(segEnd),
            )
            val dc = defaultTemplate.count(Query(segCriteria), col)
            val hc = heavyTemplate.count(Query(segCriteria), col)
            if (dc != hc) mismatches += "$segStart~$segEnd"
        }
        return mismatches
    }

    /** §3.17.4.3：关键字段聚合 checksum。 */
    fun checksumSnapshot(
        template: MongoTemplate,
        collectionName: String,
        projectId: String,
    ): ChecksumSnapshot {
        val agg = template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where(PROJECT_FIELD).`is`(projectId)),
                Aggregation.group(PROJECT_FIELD)
                    .count().`as`("count")
                    .sum(
                        org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                            .`when`(Criteria.where("deleted").ne(null)).then(1).otherwise(0)
                    ).`as`("deletedCount")
                    .max("lastModifiedDate").`as`("maxLastModified"),
            ),
            collectionName,
            Document::class.java,
        )
        val doc = agg.mappedResults.firstOrNull() ?: return ChecksumSnapshot(0, 0, null)
        val maxLm = doc["maxLastModified"]
        val maxMillis = when (maxLm) {
            is java.util.Date -> maxLm.time
            is LocalDateTime -> maxLm.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> null
        }
        return ChecksumSnapshot(
            count = (doc["count"] as? Number)?.toLong() ?: 0L,
            deletedCount = (doc["deletedCount"] as? Number)?.toLong() ?: 0L,
            maxLastModifiedEpochMillis = maxMillis,
        )
    }

    fun checksumsEqual(a: ChecksumSnapshot, b: ChecksumSnapshot): Boolean =
        a.count == b.count && a.deletedCount == b.deletedCount &&
            a.maxLastModifiedEpochMillis == b.maxLastModifiedEpochMillis

    /** 近 [withinHours] 小时内 lastModified 文档 count 一致，且全局 max(lastModifiedDate) 一致。 */
    fun lastModifiedAligned(
        projectId: String,
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        withinHours: Long = 1,
    ): Boolean {
        val col = shardCollection(projectId)
        val since = Instant.now().minus(withinHours, ChronoUnit.HOURS)
        val recentCriteria = Criteria().andOperator(
            projectCriteria(projectId),
            Criteria.where("lastModifiedDate").gte(since),
        )
        val dc = defaultTemplate.count(Query(recentCriteria), col)
        val hc = heavyTemplate.count(Query(recentCriteria), col)
        if (dc != hc) return false
        val dSnap = checksumSnapshot(defaultTemplate, col, projectId)
        val hSnap = checksumSnapshot(heavyTemplate, col, projectId)
        return dSnap.maxLastModifiedEpochMillis == hSnap.maxLastModifiedEpochMillis
    }

    /** SYNC_JOB：CATCH_UP 最后事件 clusterTime 与当前 primary optime 差值（秒）。 */
    fun catchUpLagSeconds(
        defaultTemplate: MongoTemplate,
        lastEventClusterTimeSecs: Long?,
    ): Long? {
        if (lastEventClusterTimeSecs == null) return null
        val status = runCatching {
            defaultTemplate.db.runCommand(Document("replSetGetStatus", 1))
        }.getOrNull() ?: return null
        val primary = status.getList("members", Document::class.java)
            ?.firstOrNull { it.getString("stateStr") == "PRIMARY" } ?: return null
        val optimeDate = primary.getDate("optimeDate") ?: return null
        return (optimeDate.time / 1000) - lastEventClusterTimeSecs
    }

    /** §3.17.4.4：全量 _id 集合差集（仅项目分表，切流后稳定期使用）。 */
    fun fullIdScanDiff(
        projectId: String,
        primaryTemplate: MongoTemplate,
        secondaryTemplate: MongoTemplate,
    ): Pair<Set<ObjectId>, Set<ObjectId>> {
        val col = shardCollection(projectId)
        val criteria = projectCriteria(projectId)
        val primaryIds = primaryTemplate.find(
            Query(criteria).apply { fields().include(ID) },
            Document::class.java,
            col,
        ).mapNotNull { it.getObjectId(ID) }.toSet()
        val secondaryIds = secondaryTemplate.find(
            Query(criteria).apply { fields().include(ID) },
            Document::class.java,
            col,
        ).mapNotNull { it.getObjectId(ID) }.toSet()
        return primaryIds - secondaryIds to secondaryIds - primaryIds
    }

    fun documentsEqual(a: Document, b: Document, excludeMetaFields: Boolean = true): Boolean {
        val excludeFields = if (excludeMetaFields) setOf("_class", "version") else emptySet()
        val aFiltered = a.filterKeys { it !in excludeFields }
        val bFiltered = b.filterKeys { it !in excludeFields }
        if (aFiltered.size != bFiltered.size) return false
        return aFiltered.all { (key, value) ->
            bFiltered[key]?.let { deepEquals(value, it) } ?: false
        }
    }

    /** 双写期：从主路径（Heavy）补齐副路径（Default）缺失文档。 */
    fun repairSecondaryFromPrimary(
        projectId: String,
        primaryTemplate: MongoTemplate,
        secondaryTemplate: MongoTemplate,
        shardIndices: List<Int>,
        batchSize: Int = 500,
    ) {
        for (shardIdx in shardIndices) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            var pageLastId = MIN_OBJECT_ID
            do {
                val query = Query(
                    Criteria().andOperator(
                        projectCriteria(projectId),
                        Criteria.where(ID).gt(pageLastId),
                    ),
                ).limit(batchSize).with(Sort.by(Sort.Direction.ASC, ID))
                val docs = primaryTemplate.find(query, Document::class.java, col)
                if (docs.isEmpty()) break
                docs.forEach { doc ->
                    val docId = doc.getObjectId(ID) ?: return@forEach
                    val upsertQuery = Query(Criteria.where(ID).`is`(docId))
                    val update = Update()
                    doc.entries.forEach { (k, v) -> if (k != ID) update.set(k, v) }
                    secondaryTemplate.upsert(upsertQuery, update, col)
                }
                pageLastId = docs.last().getObjectId(ID) ?: break
            } while (docs.size == batchSize)
        }
    }

    fun persistLog(
        defaultTemplate: MongoTemplate,
        projectId: String,
        checkType: String,
        passed: Boolean,
        detail: String,
    ) {
        runCatching {
            defaultTemplate.insert(
                Document().apply {
                    put("projectId", projectId)
                    put("checkType", checkType)
                    put("passed", passed)
                    put("detail", detail)
                    put("createdAt", LocalDateTime.now().toString())
                },
                RECONCILIATION_LOG_COLLECTION,
            )
        }.onFailure {
            logger.warn("Failed to persist reconciliation log: {}", it.message)
        }
    }

    fun clusterTimeSeconds(clusterTime: BsonTimestamp?): Long? =
        clusterTime?.let { it.time.toLong() and 0xFFFFFFFFL }

    private fun sampleDocsMismatch(
        defaultTemplate: MongoTemplate,
        heavyTemplate: MongoTemplate,
        collectionName: String,
        criteria: Criteria,
        sampleSize: Int,
    ): Boolean {
        val docs = defaultTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sample(sampleSize.coerceAtMost(SAMPLES_PER_SEGMENT).toLong()),
            ),
            collectionName,
            Document::class.java,
        ).mappedResults
        if (docs.isEmpty()) return false
        for (defaultDoc in docs) {
            val id = defaultDoc.getObjectId(ID) ?: continue
            val heavyDoc = heavyTemplate.findById(id, Document::class.java, collectionName)
                ?: return true
            if (!documentsEqual(defaultDoc, heavyDoc)) return true
        }
        return false
    }

    private fun deepEquals(a: Any?, b: Any?): Boolean = when {
        a is Document && b is Document -> documentsEqual(a, b, excludeMetaFields = false)
        a is List<*> && b is List<*> -> a.size == b.size && a.zip(b).all { (x, y) -> deepEquals(x, y) }
        else -> a == b
    }

    private val MIN_OBJECT_ID = ObjectId("000000000000000000000000")

    private const val NODE_COLLECTION_PREFIX = "node_"
    private const val PROJECT_FIELD = "projectId"
    private const val SHARDING_COUNT = 256
    const val RECONCILIATION_LOG_COLLECTION = "node_reconciliation_log"
    const val SEGMENT_COUNT = 10
    const val SAMPLES_PER_SEGMENT = 100
    const val LATEST_SAMPLE_SIZE = 200
    const val CATCH_UP_LAG_THRESHOLD_SEC = 5L

    private val logger = LoggerFactory.getLogger(NodeReconciliationHelper::class.java)
}
