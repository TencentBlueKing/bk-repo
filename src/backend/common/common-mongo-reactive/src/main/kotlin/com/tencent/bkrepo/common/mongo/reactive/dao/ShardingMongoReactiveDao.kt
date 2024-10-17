/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.mongo.reactive.dao

import com.mongodb.BasicDBList
import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKey
import com.tencent.bkrepo.common.mongo.util.MongoIndexResolver
import com.tencent.bkrepo.common.mongo.util.ShardingUtils
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.lang3.reflect.FieldUtils
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.query.Query
import java.lang.reflect.Field
import javax.annotation.PostConstruct

abstract class ShardingMongoReactiveDao<E> : AbstractMongoReactiveDao<E>() {

    @Suppress("LateinitUsage")
    @Autowired
    lateinit var reactiveMongoOperations: ReactiveMongoOperations

    @Value("\${sharding.count:#{null}}")
    private val fixedShardingCount: Int? = null

    /**
     * 分表Field
     */
    private val shardingField: Field

    /**
     * 分表列名
     */
    private val shardingColumn: String

    /**
     * 分表数
     */
    private var shardingCount: Int

    init {
        @Suppress("LeakingThis")
        val fieldsWithShardingKey = FieldUtils.getFieldsListWithAnnotation(classType, ShardingKey::class.java)
        require(fieldsWithShardingKey.size == 1) {
            "Only one field could be annotated with ShardingKey annotation but find ${fieldsWithShardingKey.size}!"
        }

        this.shardingField = fieldsWithShardingKey[0]
        this.shardingColumn = determineShardingColumn()

        val shardingKey = AnnotationUtils.getAnnotation(shardingField, ShardingKey::class.java)!!
        this.shardingCount = ShardingUtils.shardingCountFor(shardingKey.count)
    }

    @PostConstruct
    private fun init() {
        updateShardingCountIfNecessary()
        ensureIndex()
    }

    private fun ensureIndex() {
        if (shardingCount < 0) {
            return
        }
        val start = System.currentTimeMillis()
        val indexDefinitions = MongoIndexResolver.resolveIndexFor(classType)
        val nonexistentIndexDefinitions = filterExistedIndex(indexDefinitions)
        nonexistentIndexDefinitions.forEach {
            for (i in 1..shardingCount) {
                val reactiveMongoOperations = determineReactiveMongoOperations()
                val collectionName = parseSequenceToCollectionName(i - 1)
                reactiveMongoOperations.indexOps(collectionName).ensureIndex(it)
                    .subscribe { indexName ->
                        logger.info("Ensure index [$indexName] for sharding collection.")
                    }
            }
        }

        val indexCount = shardingCount * indexDefinitions.size
        val consume = System.currentTimeMillis() - start

        logger.info("Ensure [$indexCount] index for sharding collection [$collectionName], consume [$consume] ms.")
    }

    private fun updateShardingCountIfNecessary() {
        if (fixedShardingCount != null) {
            this.shardingCount = fixedShardingCount
        }
    }

    override fun determineReactiveMongoOperations(): ReactiveMongoOperations {
        return reactiveMongoOperations
    }

    override fun determineCollectionName(query: Query): String {
        val shardingValue = determineCollectionName(query.queryObject)
        requireNotNull(shardingValue) { "Sharding value can not empty !" }

        return shardingKeyToCollectionName(shardingValue)
    }

    override fun determineCollectionName(entity: E): String {
        val shardingValue = FieldUtils.readField(shardingField, entity, true)
        requireNotNull(shardingValue) { "Sharding value can not be empty !" }

        return shardingKeyToCollectionName(shardingValue)
    }

    override fun determineCollectionName(): String {
        if (classType.isAnnotationPresent(ShardingDocument::class.java)) {
            val document = classType.getAnnotation(ShardingDocument::class.java)
            return document.collection
        }
        return super.determineCollectionName()
    }

    fun determineCollectionName(document: Document): Any? {
        for ((key, value) in document) {
            if (key == shardingColumn) return value
            if (key == "\$and") {
                require(value is BasicDBList)
                determineCollectionName(value)?.let { return it }
            }
        }
        return null
    }

    /**
     * 支持查询条件不包含sharding key的分页查询
     *
     * @param pageRequest 分页信息
     * @param query 查询条件，不要在其中包含分页查询条件
     */
    suspend fun pageWithoutShardingKey(pageRequest: PageRequest, query: Query): Page<E> {
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
        val template = determineReactiveMongoOperations()
        for (sequence in 0 until shardingCount) {
            // 重置需要跳过的记录数量和limit
            query.skip(0L)
            query.limit(0)

            val collectionName = parseSequenceToCollectionName(sequence)

            // 统计总数
            val count = template.count(query, classType, collectionName).awaitSingle()
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
                val nodes = template.find(query, classType, collectionName).collectList().awaitSingle()
                // 更新还需要的数据数
                limit -= nodes.size
                result.addAll(nodes)
            }
            preIndex = curIndex
        }

        return PageImpl(result, pageRequest, total)
    }

    private fun determineCollectionName(list: BasicDBList): Any? {
        for (element in list) {
            require(element is Document)
            determineCollectionName(element)?.let { return it }
        }
        return null
    }

    private fun shardingKeyToCollectionName(shardValue: Any): String {
        val shardingSequence = ShardingUtils.shardingSequenceFor(shardValue, shardingCount)
        return parseSequenceToCollectionName(shardingSequence)
    }

    fun parseSequenceToCollectionName(sequence: Int): String {
        return collectionName + "_" + sequence
    }

    private fun determineShardingColumn(): String {
        val shardingKey = AnnotationUtils.getAnnotation(shardingField, ShardingKey::class.java)!!
        if (shardingKey.column.isNotEmpty()) {
            return shardingKey.column
        }
        val fieldJavaClass = org.springframework.data.mongodb.core.mapping.Field::class.java
        val fieldAnnotation = AnnotationUtils.getAnnotation(shardingField, fieldJavaClass)
        if (fieldAnnotation != null && fieldAnnotation.value.isNotEmpty()) {
            return fieldAnnotation.value
        }
        return shardingField.name
    }

    private fun filterExistedIndex(indexDefinitions: List<IndexDefinition>): List<IndexDefinition> {
        val reactiveMongoOperations = determineReactiveMongoOperations()
        val collectionName = parseSequenceToCollectionName(0)
        val indexInfoList = reactiveMongoOperations.indexOps(collectionName).indexInfo
        return indexInfoList.map { index -> index.name }.collectList().map { indexNameList ->
            val filteredList = indexDefinitions.filter { index ->
                val indexOptions = index.indexOptions
                if (indexOptions.contains("name")) {
                    val indexName = indexOptions.getString("name")
                    !indexNameList.contains(indexName)
                } else true
            }
            filteredList
        }.block() ?: emptyList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ShardingMongoReactiveDao::class.java)
        private const val MAX_SHARDING_COUNT_OF_PAGE_QUERY = 256
    }
}
