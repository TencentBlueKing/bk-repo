package com.tencent.bkrepo.common.mongo.reactive.dao

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.mongo.api.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils
import com.tencent.bkrepo.common.mongo.util.MongoIndexResolver
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.index.IndexDefinition
import java.util.concurrent.TimeUnit

/**
 * 按月分表的 Reactive DAO
 *
 * 索引只有在 insert/save 时才会创建
 */
abstract class MonthRangeShardingMongoReactiveDao<E> : RangeShardingMongoReactiveDao<E>() {

    private val indexCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build<String, Boolean>()

    override fun determineShardingUtils(): ShardingUtils {
        return MonthRangeShardingUtils
    }

    override suspend fun insert(entity: E): E {
        ensureIndex(entity)
        return super.insert(entity)
    }

    override suspend fun insert(entityCollection: Collection<E>): Collection<E> {
        ensureIndex(entityCollection.first())
        return super.insert(entityCollection)
    }

    override suspend fun save(entity: E): E {
        ensureIndex(entity)
        return super.save(entity)
    }

    private fun getIndexCacheKey(
        template: org.springframework.data.mongodb.core.ReactiveMongoTemplate,
        collectionName: String,
        indexDefinition: IndexDefinition,
    ): String {
        return "${System.identityHashCode(template)}:$collectionName:${indexDefinition.indexKeys.keys}"
    }

    private fun ensureIndex(entity: E) {
        val collectionName = determineCollectionName(entity)
        val indexDefinitions = MongoIndexResolver.resolveIndexFor(classType)
        val templates = writeReactiveTemplates(collectionName, entity)
        indexDefinitions.forEach { indexDefinition ->
            templates.forEach { template ->
                val indexCacheKey = getIndexCacheKey(template, collectionName, indexDefinition)
                if (indexCache.getIfPresent(indexCacheKey) == true) {
                    return@forEach
                }
                template.indexOps(collectionName).ensureIndex(indexDefinition)
                    .doOnSuccess { indexName ->
                        indexCache.put(indexCacheKey, true)
                        logger.info("$collectionName create Index: $indexName")
                    }
                    .subscribe()
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MonthRangeShardingMongoReactiveDao::class.java)
    }
}
