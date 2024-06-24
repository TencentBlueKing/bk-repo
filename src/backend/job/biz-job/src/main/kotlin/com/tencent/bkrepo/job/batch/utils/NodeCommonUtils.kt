package com.tencent.bkrepo.job.batch.utils

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.exception.RepoMigratingException
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
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
        lateinit var mongoTemplate: MongoTemplate
        lateinit var migrateRepoStorageService: MigrateRepoStorageService
        lateinit var separationTaskService: SeparationTaskService
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val SEPARATION_COLLECTION_NAME_PREFIX = "separation_node_"
        private val workPool = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            1L,
            TimeUnit.MINUTES,
            ArrayBlockingQueue(DEFAULT_BUFFER_SIZE),
            ThreadFactoryBuilder().setNameFormat("node-utils-%d").build(),
        )

        fun findNodes(query: Query, storageCredentialsKey: String?): List<Node> {
            val nodes = mutableListOf<Node>()
            (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.forEach { collection ->
                val find = mongoTemplate.find(query, Node::class.java, collection).filter {
                    val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                    val key = if (migrateRepoStorageService.migrating(it.projectId, it.repoName)) {
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

        fun exist(query: Query, storageCredentialsKey: String?): Boolean {
            for (i in 0 until SHARDING_COUNT) {
                val collection = COLLECTION_NAME_PREFIX.plus(i)
                val find = mongoTemplate.find(query, Node::class.java, collection)
                    .distinctBy { it.projectId + it.repoName }
                    .any {
                        // node正在迁移时无法判断是否存在于存储[storageCredentialsKey]上
                        if (migrateRepoStorageService.migrating(it.projectId, it.repoName)) {
                            throw RepoMigratingException("repo[${it.projectId}/${it.repoName}] was migrating")
                        }
                        val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                        repo.storageCredentials?.key == storageCredentialsKey
                    }
                if (find) {
                    return true
                }
            }
            // 加上冷表检查
            return separationNodeExist(query, storageCredentialsKey)
        }

        private fun separationNodeExist(query: Query, storageCredentialsKey: String?): Boolean {
            val separationDates = separationTaskService.findDistinctSeparationDate()
            for (date in separationDates) {
                val collection = SEPARATION_COLLECTION_NAME_PREFIX.plus(
                    MonthRangeShardingUtils.shardingSequencesFor(date, 1)
                )
                val find = mongoTemplate.find(query, Node::class.java, collection)
                    .distinctBy { it.projectId + it.repoName }
                    .any {
                        // node正在迁移时无法判断是否存在于存储[storageCredentialsKey]上
                        if (migrateRepoStorageService.migrating(it.projectId, it.repoName)) {
                            throw RepoMigratingException("repo[${it.projectId}/${it.repoName}] was migrating")
                        }
                        val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                        repo.storageCredentials?.key == storageCredentialsKey
                    }
                if (find) {
                    return true
                }
            }
            return false
        }

        fun forEachNodeByCollectionParallel(
            query: Query,
            batchSize: Int = BATCH_SIZE,
            consumer: Consumer<Map<String, Any?>>,
        ) {
            val futures = mutableListOf<Future<*>>()
            for (i in 0 until SHARDING_COUNT) {
                val collection = COLLECTION_NAME_PREFIX.plus(i)
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
                    MonthRangeShardingUtils.shardingSequencesFor(date, 1)
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
    }
}
