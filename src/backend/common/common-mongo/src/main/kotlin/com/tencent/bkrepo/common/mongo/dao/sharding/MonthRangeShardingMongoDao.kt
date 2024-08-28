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

package com.tencent.bkrepo.common.mongo.dao.sharding

import com.google.common.cache.CacheBuilder
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.util.MongoIndexResolver
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.dao.util.sharding.ShardingUtils
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.concurrent.TimeUnit

abstract class MonthRangeShardingMongoDao<E> : RangeShardingMongoDao<E>() {

    private val indexCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build<String, Boolean>()

    override fun determineShardingUtils(): ShardingUtils {
        return MonthRangeShardingUtils
    }

    override fun <E> find(query: Query, clazz: Class<E>): List<E> {
        ensureIndex(query)
        return super.find(query, clazz)
    }

    override fun upsert(query: Query, update: Update): UpdateResult {
        ensureIndex(query)
        return super.upsert(query, update)
    }

    override fun insert(entity: E): E {
        ensureIndex(entity)
        return super.insert(entity)
    }

    override fun insert(entityCollection: Collection<E>): Collection<E> {
        ensureIndex(entityCollection.first())
        return super.insert(entityCollection)
    }

    override fun save(entity: E): E {
        ensureIndex(entity)
        return super.save(entity)
    }

    private fun getIndexCacheKey(collectionName: String, indexDefinition: IndexDefinition): String {
        return collectionName + indexDefinition.indexKeys.keys
    }

    private fun ensureIndex(entity: E) {
        val collectionName = determineCollectionName(entity)
        createIndex(collectionName)
    }

    private fun ensureIndex(query: Query) {
        val collectionName = determineCollectionName(query)
        createIndex(collectionName)
    }

    private fun createIndex(collectionName: String) {
        val indexDefinitions = MongoIndexResolver.resolveIndexFor(classType)
        indexDefinitions.forEach {
            val indexCacheKey = getIndexCacheKey(collectionName, it)
            if (indexCache.getIfPresent(indexCacheKey) != true) {
                determineMongoTemplate().indexOps(collectionName).ensureIndex(it)
                indexCache.put(indexCacheKey, true)
                logger.info("$collectionName create Index: $it")
            }
        }
    }
}
