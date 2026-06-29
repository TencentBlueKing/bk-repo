package com.tencent.bkrepo.common.mongo.reactive.dao

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.mongo.api.util.MongoDaoHelper.shardingValuesOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

abstract class RangeShardingMongoReactiveDao<E> : ShardingMongoReactiveDao<E>() {

    override suspend fun <T> findOne(query: Query, clazz: Class<T>): T? {
        val list = mutableListOf<T>()
        val collectionNames = determineCollectionNames(query)
        val template = determineReactiveMongoOperations()
        collectionNames.forEach {
            val result = template.findOne(query, clazz, it).awaitSingleOrNull()
            result?.let { r -> list.add(r) }
        }
        sortQueryResultList(query, list, clazz)
        return list.firstOrNull()
    }

    override suspend fun <T> find(query: Query, clazz: Class<T>): List<T> {
        val shardingField = shardingFields.values.first()
        if (!queryWithPage(query)) {
            throw UnsupportedOperationException("query needs page request")
        }
        if (query.sortObject.keys.isNotEmpty() && query.sortObject.keys.first() != shardingField.name) {
            throw UnsupportedOperationException("query with sort needs first sort key is sharding field")
        }

        var collectionNames = determineCollectionNames(query)
        val countMap = count(collectionNames, query)

        val direction = if (query.sortObject[shardingField.name] == 1) Sort.Direction.ASC else Sort.Direction.DESC
        if (direction == Sort.Direction.DESC) {
            collectionNames = collectionNames.reversed()
        }

        val list = mutableListOf<T>()
        var limit = query.limit
        var skip = query.skip
        val queryWithoutPage = dropQueryPageRequest(query)
        val template = determineReactiveMongoOperations()

        collectionNames.forEach {
            if (list.size == query.limit) {
                return@forEach
            }
            val count = countMap[it] ?: 0L
            val diff = count - skip
            if (diff <= 0) {
                skip = -diff
            } else {
                queryWithoutPage.skip(skip).limit(limit)
                val result = template.find(queryWithoutPage, clazz, it).collectList().awaitSingle()
                list.addAll(result)
                skip = 0
                limit -= result.size
            }
        }
        return list
    }

    override suspend fun remove(query: Query): DeleteResult {
        val shardingValue = shardingValuesOf(query.queryObject, shardingFields)?.firstOrNull()
        if (shardingValue is Document && shardingValue.size > 1) {
            throw IllegalArgumentException("Remove only works on particular table!")
        }
        val collectionName = determineCollectionName(query)
        return determineReactiveMongoOperations()
            .remove(query, collectionName)
            .awaitSingle()
    }

    override suspend fun count(query: Query): Long {
        val collectionNames = determineCollectionNames(query)
        val countMap = count(collectionNames, query)
        return countMap.values.sum()
    }

    override suspend fun exists(query: Query): Boolean {
        val collectionName = determineCollectionName(query)
        return determineReactiveMongoOperations().exists(query, collectionName).awaitSingle()
    }

    override suspend fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): MutableList<O> {
        throw UnsupportedOperationException()
    }

    fun determineCollectionNames(query: Query): List<String> {
        val shardingValues = shardingValuesOf(query.queryObject, shardingFields)
        val shardingValue = shardingValues?.firstOrNull()
        require(shardingValue is Document && shardingValue.size == 2) { "Sharding value can not empty!" }
        return shardingUtils.shardingSequencesFor(shardingValues, shardingCount)
            .map { collectionName + "_" + it }
    }

    private suspend fun count(collectionNames: List<String>, query: Query): Map<String, Long> = coroutineScope {
        val queryWithoutPage = dropQueryPageRequest(query)
        val template = determineReactiveMongoOperations()
        collectionNames.map { name ->
            async { name to template.count(queryWithoutPage, name).awaitSingle() }
        }.awaitAll().toMap()
    }

    private fun queryWithPage(query: Query): Boolean {
        return query.limit != 0
    }

    private fun <T> sortQueryResultList(query: Query, list: MutableList<T>, clazz: Class<T>) {
        val sort = query.sortObject
        list.sortWith { o1, o2 -> o1.compareTo(o2, sort, clazz) }
    }

    private fun dropQueryPageRequest(query: Query): Query {
        val queryWithoutPage = Query(buildCriteria(query.queryObject))
        val orders = query.sortObject.map { (k, v) ->
            val direction = if (v == 1) Sort.Direction.ASC else Sort.Direction.DESC
            Sort.Order(direction, k)
        }
        return queryWithoutPage.with(Sort.by(orders))
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
        } catch (_: Exception) {
        }
        return c
    }
}

private fun <T> T.compareTo(other: T, sort: Document, clazz: Class<T>): Int {
    var compareResult = 0
    sort.keys.forEach {
        val direction = sort[it] as Int
        val fieldName = it.replaceFirst(it[0], it[0].uppercaseChar())
        val method = clazz.getMethod("get$fieldName")
        val value1 = method.invoke(this).toString()
        val value2 = method.invoke(other).toString()
        if (value1 != value2) {
            compareResult = direction * value1.compareTo(value2)
            return@forEach
        }
    }
    return compareResult
}
