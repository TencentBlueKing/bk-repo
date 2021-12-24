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

import com.mongodb.client.result.DeleteResult
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class RangeShardingMongoDao<E> : ShardingMongoDao<E>() {

    private val executors = ThreadPoolExecutor(
        0,
        Runtime.getRuntime().availableProcessors(),
        60,
        TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(1024)
    )

    override fun <T> findOne(query: Query, clazz: Class<T>): T? {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao find: [$query], ${query.limit}, ${query.skip}")
        }
        val list = mutableListOf<T>()
        val collectionNames = determineCollectionNames(query)
        collectionNames.forEach {
            val result = determineMongoTemplate().findOne(query, clazz, it)
            result?.apply { list.add(result) }
        }

        sortQueryResultList(query, list, clazz)
        return list.firstOrNull()
    }

    /**
     * 无分页：不支持此操作
     * 排序首条件非分页字段
     */
    override fun <T> find(query: Query, clazz: Class<T>): List<T> {
        if (logger.isDebugEnabled) {
            logger.debug("Mongo Dao find: [$query], ${query.limit}, ${query.skip}")
        }
        val list = mutableListOf<T>()
        var collectionNames = determineCollectionNames(query)
        if (!queryWithPage(query)) {
            throw UnsupportedOperationException("query needs page request")
        }
        if (query.sortObject.keys.isNotEmpty() && query.sortObject.keys.first() != shardingField.name) {
            throw UnsupportedOperationException("query with sort needs first sort key is sharding field")
        }
        val countMap = ConcurrentHashMap<String, Long>()
        val queryWithoutPage = dropQueryPageRequest(query)
        count(collectionNames, countMap, queryWithoutPage)
        val direction = if (query.sortObject[shardingField.name] == 1) Sort.Direction.ASC else Sort.Direction.DESC
        if (direction == Sort.Direction.DESC) {
            collectionNames = collectionNames.reversed()
        }
        var limit = query.limit
        var skip = query.skip
        collectionNames.forEach {
            logger.debug("$it, $queryWithoutPage, $limit, $skip")
            if (list.size == query.limit) {
                return@forEach
            }
            val count = countMap[it]!!
            val diff = count - skip
            if (diff <= 0) {
                skip = -diff
            } else {
                queryWithoutPage.skip(skip).limit(limit)
                val result = determineMongoTemplate().find(queryWithoutPage, clazz, it)
                list.addAll(result)
                skip = 0
                limit -= result.size
            }
        }
        return list
    }

    override fun remove(query: Query): DeleteResult {
        throw UnsupportedOperationException()
    }

    private fun <T> getSortedCollection(
        queryWithoutPage: Query,
        skip: Int,
        limit: Int,
        clazz: Class<T>,
        collectionNames: List<String>
    ): List<T> {
        var index = 1
        var collection = determineMongoTemplate().find(queryWithoutPage, clazz, collectionNames.first())
        while (index < collectionNames.size) {
            val nextCollection = determineMongoTemplate().find(queryWithoutPage, clazz, collectionNames[index])
            val size = collection.size + nextCollection.size
            if (size < skip) {
                collection.addAll(nextCollection)
            } else {
                collection = mergeSort(
                    collection = collection + nextCollection,
                    sort = queryWithoutPage.sortObject,
                    clazz = clazz
                ).subList(skip, skip + limit)
            }
            index++
        }
        return collection
    }

    override fun count(query: Query): Long {
        val countMap = ConcurrentHashMap<String, Long>()
        val collectionNames = determineCollectionNames(query)
        count(collectionNames, countMap, query)
        return countMap.values.sum()
    }

    override fun exists(query: Query): Boolean {
        val collectionName = determineCollectionName(query)
        return determineMongoTemplate().exists(query, collectionName)
    }

    override fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): AggregationResults<O> {
        throw UnsupportedOperationException()
    }

    private fun <T> mergeSort(collection: List<T>, sort: Document, clazz: Class<T>): List<T> {
        val mid = collection.size / 2
        val left = collection.subList(0, mid)
        val right = collection.subList(mid, collection.size - 1)
        return merge(mergeSort(left, sort, clazz), mergeSort(right, sort, clazz), sort, clazz)
    }

    private fun <T> merge(left: List<T>, right: List<T>, sort: Document, clazz: Class<T>): List<T> {
        val size = left.size + right.size
        val result = ArrayList<T>(size)
        var index = 0
        var i = 0
        var j = 0
        while (index < size) {
            when {
                i >= left.size -> result[index] = right[j++]
                j >= right.size -> result[index] = left[i++]
                left[i].compareTo(right[j], sort, clazz) > 0 -> result[index] = right[j++]
                else -> result[index] = left[i++]
            }
            index++
        }
        return result
    }

    private fun queryWithPage(query: Query): Boolean {
        return query.limit != 0
    }

    private fun queryWithSort(query: Query): Boolean {
        return query.sortObject.isNotEmpty()
    }

    private fun shardingFieldIsFirstSortKey(query: Query): Boolean {
        return query.sortObject.keys.firstOrNull() == shardingField.name
    }

    private fun count(
        collectionNames: List<String>,
        countMap: ConcurrentHashMap<String, Long>,
        query: Query
    ): ConcurrentHashMap<String, Long> {
        val latch = CountDownLatch(collectionNames.size)
        collectionNames.forEach {
            executors.submit {
                countMap[it] = determineMongoTemplate().count(query, it)
                latch.countDown()
            }
        }
        latch.await()
        return countMap
    }

    private fun <T> sortQueryResultList(query: Query, list: MutableList<T>, clazz: Class<T>) {
        val sort = query.sortObject
        list.sortWith(Comparator { o1, o2 ->
            o1.compareTo(o2, sort, clazz)
        })
    }

    private fun dropQueryPageRequest(query: Query): Query {
        val queryWithOutPage = Query(buildCriteria(query.queryObject))
        val orders = query.sortObject.map { (k, v) ->
            val direction = if (v == 1) Sort.Direction.ASC else Sort.Direction.DESC
            Sort.Order(direction, k)
        }
        return queryWithOutPage.with(Sort.by(orders))
    }

    fun determineCollectionNames(query: Query): List<String> {
        val shardingValue = determineCollectionName(query.queryObject)
        require(shardingValue is Document && shardingValue.size == 2) { "Sharding value can not empty !" }

        return shardingUtils.shardingSequencesFor(shardingValue, shardingCount).map { collectionName + "_" + it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildCriteria(document: Document): Criteria {
        val c = Criteria()
        try {
            val criteriaField = c.javaClass.getDeclaredField("criteria")
            criteriaField.isAccessible = true
            val criteria = criteriaField.get(c) as LinkedHashMap<String, Any>
            for ((key, value) in document) {
                criteria[key] = value
            }
            val criteriaChainField = c.javaClass.getDeclaredField("criteriaChain")
            criteriaChainField.isAccessible = true
            val criteriaChain = criteriaChainField[c] as MutableList<Criteria>
            criteriaChain.add(c)
        } catch (e: Exception) {
            // Ignore
        }
        return c
    }
}

private fun <T> T.compareTo(other: T, sort: Document, clazz: Class<T>): Int {
    var compareResult = 0
    sort.keys.forEach {
        val direction = sort[it] as Int
        val filedName = it.replaceFirst(it[0], it[0].toUpperCase())
        val method = clazz.getMethod("get$filedName")
        val value1 = method.invoke(this).toString()
        val value2 = method.invoke(other).toString()
        if (value1 != value2) {
            compareResult = direction * value1.compareTo(value2)
            return@forEach
        }
    }
    return compareResult
}
