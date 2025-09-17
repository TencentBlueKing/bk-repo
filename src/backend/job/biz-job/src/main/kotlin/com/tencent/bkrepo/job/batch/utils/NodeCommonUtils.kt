package com.tencent.bkrepo.job.batch.utils

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.api.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.COLLECTION_NAME_NODE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.exception.RepoMigratingException
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Component
class NodeCommonUtils(
    mongoTemplate: MongoTemplate,
    migrateRepoStorageService: MigrateRepoStorageService,
    separationTaskService: SeparationTaskService
) {
    init {
        Companion.mongoTemplate = mongoTemplate
        Companion.migrateRepoStorageService = migrateRepoStorageService
        Companion.separationTaskService = separationTaskService
    }

    data class ProjectRepo(
        val projectId: String,
        val repoName: String,
    )

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String,
        val size: Long,
        val lastAccessDate: LocalDateTime? = null,
        val archived: Boolean?,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(NodeCommonUtils::class.java)
        lateinit var mongoTemplate: MongoTemplate
        lateinit var migrateRepoStorageService: MigrateRepoStorageService
        lateinit var separationTaskService: SeparationTaskService
        private const val COLLECTION_NAME_PREFIX = "node_"
        const val SEPARATION_COLLECTION_NAME_PREFIX = "separation_node_"
        private val workPool = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            1L,
            TimeUnit.MINUTES,
            ArrayBlockingQueue(DEFAULT_BUFFER_SIZE),
            ThreadFactoryBuilder().setNameFormat("node-utils-%d").build(),
        )

        fun findNodes(query: Query, storageCredentialsKey: String?, checkMigrating: Boolean = true): List<Node> {
            val nodes = mutableListOf<Node>()
            (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.forEach { collection ->
                val find = mongoTemplate.find(query, Node::class.java, collection).filter {
                    val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                    val key = if (checkMigrating && migrateRepoStorageService.migrating(it.projectId, it.repoName)) {
                        repo.oldCredentialsKey
                    } else {
                        repo.storageCredentials?.key
                    }
                    key == storageCredentialsKey
                }
                nodes.addAll(find)
            }
            return nodes
        }

        fun nodeExist(query: Query, storageCredentialsKey: String?): Boolean {
            if (exist(COLLECTION_NAME_NODE, SHARDING_COUNT, query, storageCredentialsKey)) {
                return true
            }
            // 加上冷表检查
            return separationNodeExist(query, storageCredentialsKey)
        }

        fun exist(
            collection: String,
            shardingCount: Int = SHARDING_COUNT,
            query: Query,
            storageCredentialsKey: String?
        ): Boolean {
            for (i in 0 until shardingCount) {
                if (doExist("${collection}_$i", query, storageCredentialsKey)) {
                    return true
                }
            }
            return false
        }

        private fun separationNodeExist(query: Query, storageCredentialsKey: String?): Boolean {
            val separationDates = separationTaskService.findDistinctSeparationDate()
            for (date in separationDates) {
                val collection = SEPARATION_COLLECTION_NAME_PREFIX.plus(
                    MonthRangeShardingUtils.shardingSequenceFor(date, 1)
                )
                if (doExist(collection, query, storageCredentialsKey)) {
                    return true
                }
            }
            return false
        }

        fun forEachByCollectionParallel(
            collectionNamePrefix: String,
            query: Query,
            batchSize: Int = BATCH_SIZE,
            shardingCount: Int = SHARDING_COUNT,
            consumer: Consumer<Map<String, Any?>>,
        ) {
            val futures = mutableListOf<Future<*>>()
            for (i in 0 until shardingCount) {
                val collection = "${collectionNamePrefix}_$i"
                futures.add(workPool.submit { findByCollection(query, batchSize, collection, consumer) })
            }
            futures.forEach { it.get() }
        }

        fun forEachColdNodeByCollectionParallel(
            query: Query,
            batchSize: Int = BATCH_SIZE,
            consumer: Consumer<Map<String, Any?>>,
        ) {
            val futures = mutableListOf<Future<*>>()
            val separationDates = separationTaskService.findDistinctSeparationDate()
            for (date in separationDates) {
                val collection = SEPARATION_COLLECTION_NAME_PREFIX.plus(
                    MonthRangeShardingUtils.shardingSequenceFor(date, 1)
                )
                futures.add(workPool.submit { findByCollection(query, batchSize, collection, consumer) })

            }
            futures.forEach { it.get() }
        }

        fun findByCollection(
            query: Query,
            batchSize: Int,
            collection: String,
            consumer: Consumer<Map<String, Any?>>,
        ) {
            var querySize: Int
            var lastId = ObjectId(MIN_OBJECT_ID)
            do {
                val newQuery = Query.of(query)
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(batchSize)
                    .with(Sort.by(ID).ascending())
                val data = mongoTemplate.find<Map<String, Any?>>(
                    newQuery,
                    collection,
                )
                if (data.isEmpty()) {
                    break
                }
                data.forEach { consumer.accept(it) }
                querySize = data.size
                lastId = data.last()[ID] as ObjectId
            } while (querySize == batchSize)
        }

        fun findByCollectionAsync(
            query: Query,
            batchSize: Int,
            collection: String,
            consumer: Consumer<Map<String, Any?>>,
        ): Mono<Unit> {
            return Mono.fromCallable {
                findByCollection(query, batchSize, collection, consumer)
            }.publishOn(Schedulers.boundedElastic())
        }

        fun collectionNames(projectIds: List<String>): List<String> {
            val collectionNames = mutableListOf<String>()
            if (projectIds.isNotEmpty()) {
                projectIds.forEach {
                    val index = HashShardingUtils.shardingSequenceFor(it, SHARDING_COUNT)
                    collectionNames.add("${COLLECTION_NAME_PREFIX}$index")
                }
            } else {
                (0 until SHARDING_COUNT).forEach { collectionNames.add("${COLLECTION_NAME_PREFIX}$it") }
            }
            return collectionNames
        }

        @Suppress("UnstableApiUsage")
        fun buildNodeBloomFilter(expectedInsertions: Long, fpp: Double): BloomFilter<CharSequence> {
            return buildBloomFilter(expectedInsertions, fpp) { bf ->
                val query = Query(Criteria.where(FOLDER).isEqualTo(false))
                query.fields().include(SHA256)
                forEachByCollectionParallel(COLLECTION_NAME_NODE, query) {
                    it[SHA256]?.toString()?.let { sha256 -> bf.put(sha256) }
                }

                //加上冷表检查
                forEachColdNodeByCollectionParallel(query) {
                    it[SHA256]?.toString()?.let { sha256 -> bf.put(sha256) }
                }
            }
        }

        @Suppress("UnstableApiUsage")
        fun buildBlockNodeBloomFilter(
            collectionName: String,
            shardingCount: Int,
            expectedInsertions: Long,
            fpp: Double
        ): BloomFilter<CharSequence> {
            return buildBloomFilter(expectedInsertions, fpp) { bf ->
                val query = Query().apply { fields().include(SHA256) }
                forEachByCollectionParallel(collectionName, query, BATCH_SIZE, shardingCount) {
                    it[SHA256]?.toString()?.let { sha256 -> bf.put(sha256) }
                }
            }
        }

        @Suppress("UnstableApiUsage")
        fun buildBloomFilter(
            expectedInsertions: Long,
            fpp: Double,
            filler: (bloomFilter: BloomFilter<CharSequence>) -> Unit
        ): BloomFilter<CharSequence> {
            logger.info("Start build bloom filter.")
            val bf = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp)
            filler(bf)
            val count = "${bf.approximateElementCount()}/${expectedInsertions}"
            logger.info("Build bloom filter successful,count: $count,fpp: ${bf.expectedFpp()}")
            return bf
        }

        private fun doExist(collection: String, query: Query, storageCredentialsKey: String?): Boolean {
            return mongoTemplate.find(query, ProjectRepo::class.java, collection)
                .distinctBy { it.projectId + it.repoName }
                .any {
                    // node正在迁移时无法判断是否存在于存储[storageCredentialsKey]上
                    if (migrateRepoStorageService.migrating(it.projectId, it.repoName)) {
                        throw RepoMigratingException("repo[${it.projectId}/${it.repoName}] was migrating")
                    }
                    val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                    repo.storageCredentials?.key == storageCredentialsKey
                }
        }
    }
}
