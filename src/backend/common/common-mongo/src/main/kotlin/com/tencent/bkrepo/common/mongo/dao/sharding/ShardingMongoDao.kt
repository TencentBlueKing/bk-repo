/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.mongo.dao.sharding

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper.determineShardingCount
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper.determineShardingFields
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper.shardingValues
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper.shardingValuesOf
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.mongo.routing.MigrationDdlGuard
import com.tencent.bkrepo.common.mongo.dao.util.MongoIndexResolver
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.query.Query
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

/**
 * mongodb 支持分表的数据访问层抽象类
 */
abstract class ShardingMongoDao<E> : AbstractMongoDao<E>() {

    // 抽象类使用构造器注入不方便
    @Suppress("LateinitUsage")
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Value("\${sharding.count:#{null}}")
    private val fixedShardingCount: Int? = null

    /**
     * 分表Field，key为列名
     */
    protected lateinit var shardingFields: LinkedHashMap<String, Field>

    /**
     * 分表数
     */
    protected var shardingCount: Int = 1

    /**
     * 分表工具类
     */
    protected val shardingUtils by lazy {
        determineShardingUtils()
    }

    @Autowired(required = false)
    private var migrationDdlGuard: MigrationDdlGuard? = null

    // lazy ensureIndex 缓存，避免每次 insert/save 都检查索引。路由热加载后首次写入自动建索引。
    private val indexCache = CacheBuilder.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build<String, Boolean>()

    @PostConstruct
    private fun init() {
        this.shardingFields = determineShardingFields(classType, customShardingColumns())
        this.shardingCount = determineShardingCount(classType, shardingUtils, customShardingCount())
        ensureIndex()
    }

    private fun ensureIndex() {
        if (shardingCount < 0) {
            return
        }
        val start = System.currentTimeMillis()
        val indexDefinitions = MongoIndexResolver.resolveIndexFor(classType)
        val nonexistentIndexDefinitions = filterExistedIndex(indexDefinitions)
        nonexistentIndexDefinitions.forEach { ensureIndexAcrossShards(it) }

        val indexCount = shardingCount * indexDefinitions.size
        val consume = System.currentTimeMillis() - start

        logger.info("Ensure [$indexCount] index for sharding collection [$collectionName], consume [$consume] ms.")
    }

    private fun ensureIndexAcrossShards(index: IndexDefinition) {
        for (i in 1..shardingCount) {
            val mongoTemplate = determineMongoTemplate()
            val collectionName = parseSequenceToCollectionName(i - 1)
            migrationDdlGuard?.assertDdlAllowed(collectionName)
            mongoTemplate.indexOps(collectionName).ensureIndex(index)
        }
    }

    private fun filterExistedIndex(indexDefinitions: List<IndexDefinition>): List<IndexDefinition> {
        val mongoTemplate = determineMongoTemplate()
        val collectionName = parseSequenceToCollectionName(0)
        val indexInfoList = mongoTemplate.indexOps(collectionName).indexInfo
        val indexNameList = indexInfoList.map { index -> index.name }
        return indexDefinitions.filter { index ->
            val indexOptions = index.indexOptions
            if (indexOptions.contains("name")) {
                val indexName = indexOptions.getString("name")
                !indexNameList.contains(indexName)
            } else true
        }
    }

    private fun shardingKeyToCollectionName(shardValues: List<Any>): String {
        return parseSequenceToCollectionName(shardingUtils.shardingSequenceFor(shardValues, shardingCount))
    }

    fun parseSequenceToCollectionName(sequence: Int): String {
        return collectionName + "_" + sequence
    }

    protected open fun customShardingColumns(): List<String> {
        return emptyList()
    }

    protected open fun customShardingCount(): Int? {
        return fixedShardingCount
    }

    override fun determineCollectionName(): String {
        return MongoDaoHelper.determineShardingCollectionName(classType) ?: super.determineCollectionName()
    }

    override fun determineMongoTemplate(): MongoTemplate {
        return this.mongoTemplate
    }

    override fun determineCollectionName(entity: E): String {
        return shardingKeyToCollectionName(shardingValues(entity as Any, shardingFields))
    }

    override fun determineCollectionName(query: Query): String {
        val shardingValues = shardingValuesOf(query.queryObject, shardingFields)
        requireNotNull(shardingValues) { "Sharding value can not empty!" }
        return shardingKeyToCollectionName(shardingValues)
    }

    override fun determineCollectionName(aggregation: Aggregation): String {
        val shardingValues = shardingValuesOf(aggregation, shardingFields)
        require(!shardingValues.isNullOrEmpty()) { "Sharding values can not be empty!" }
        return shardingKeyToCollectionName(shardingValues)
    }

    override fun <T> findAll(clazz: Class<T>): List<T> {
        throw UnsupportedOperationException()
    }

    /**
     * 支持查询条件不包含sharding key的分页查询
     *
     * @param pageRequest 分页信息
     * @param query 查询条件，不要在其中包含分页查询条件
     */
    fun pageWithoutShardingKey(pageRequest: PageRequest, query: Query): Page<E> {
        if (shardingCount <= 0 || shardingCount > MAX_SHARDING_COUNT_OF_PAGE_QUERY) {
            throw UnsupportedOperationException()
        }

        val startIndex = pageRequest.pageNumber * pageRequest.pageSize
        var limit = pageRequest.pageSize

        var preIndex = -1L
        var curIndex: Long
        var total = 0L
        val result = ArrayList<E>()

        // 遍历所有分表进行查询
        for (sequence in 0 until shardingCount) {
            // 重置需要跳过的记录数量和limit
            query.skip(0L)
            query.limit(0)

            val collectionName = parseSequenceToCollectionName(sequence)
            val template = determineReadMongoTemplate(collectionName, query)

            // 统计总数
            val count = template.count(query, classType, collectionName)
            if (count == 0L) {
                continue
            }
            total += count
            curIndex = total - 1

            // 当到达目标分页时才进行查询
            if (curIndex >= startIndex && limit > 0) {
                if (preIndex < startIndex) {
                    // 跳过属于前一个分页的数据
                    query.skip(startIndex - preIndex - 1)
                }
                query.limit(limit)
                val nodes = template.find(query, classType, collectionName)
                // 更新还需要的数据数
                limit -= nodes.size
                result.addAll(nodes)
            }
            preIndex = curIndex
        }

        return PageImpl(result, pageRequest, total)
    }

    override fun insert(entity: E): E {
        ensureIndex(entity)
        return super.insert(entity)
    }

    override fun insert(entityCollection: Collection<E>): Collection<E> {
        if (AbstractMongoDao.logger.isDebugEnabled) {
            AbstractMongoDao.logger.debug("Mongo Dao insert many: [$entityCollection]")
        }
        ensureIndex(entityCollection.first())
        checkCollectionConsistency(entityCollection)
        return super.insert(entityCollection)
    }

    override fun save(entity: E): E {
        ensureIndex(entity)
        return super.save(entity)
    }

    private fun getIndexCacheKey(collectionName: String, indexDefinition: IndexDefinition): String {
        return collectionName + indexDefinition.indexKeys.keys
    }

    // 在 insert/save 时 lazy 建索引，支持路由热加载场景（配置 projectId 无需重启）
    private fun ensureIndex(entity: E) {
        val collectionName = determineCollectionName(entity)
        val indexDefinitions = MongoIndexResolver.resolveIndexFor(classType)
        val templates = writeTemplates(collectionName, entity)
        val defaultTemplate = determineMongoTemplate()
        indexDefinitions.forEach { indexDefinition ->
            val indexCacheKey = getIndexCacheKey(collectionName, indexDefinition)
            if (indexCache.getIfPresent(indexCacheKey) != true) {
                ensureIndexOnTemplates(templates, defaultTemplate, collectionName, indexDefinition)
                indexCache.put(indexCacheKey, true)
                logger.info("$collectionName create Index: $indexDefinition")
            }
        }
    }

    private fun ensureIndexOnTemplates(
        templates: List<MongoTemplate>,
        defaultTemplate: MongoTemplate,
        collectionName: String,
        indexDefinition: IndexDefinition
    ) {
        templates.forEach { template ->
            try {
                template.indexOps(collectionName).ensureIndex(indexDefinition)
            } catch (e: Exception) {
                // secondary 上首次建唯一索引可能因已有脏数据而失败（DUAL_WRITE 期无索引约束写入），
                // 只 warn 不阻断 insert；primary 失败才抛（否则后续写入丢失唯一约束）
                if (template === defaultTemplate) throw e
                logger.error(
                    "$collectionName create index failed on non-default template, " +
                        "may need manual cleanup: ${e.message}"
                )
            }
        }
    }

    private fun checkCollectionConsistency(entityCollection: Collection<E>) {
        val sequences = entityCollection.map { determineCollectionName(it) }.distinct()
        require(sequences.size == 1)
    }

    abstract fun determineShardingUtils(): ShardingUtils

    companion object {
        private val logger = LoggerFactory.getLogger(ShardingMongoDao::class.java)
        const val MAX_SHARDING_COUNT_OF_PAGE_QUERY = 256
    }
}