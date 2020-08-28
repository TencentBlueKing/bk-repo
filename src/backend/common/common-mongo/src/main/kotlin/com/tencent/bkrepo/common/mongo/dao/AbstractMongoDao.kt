package com.tencent.bkrepo.common.mongo.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import java.lang.reflect.ParameterizedType
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.MongoCollectionUtils
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * mongo db 数据访问层抽象类
 */
abstract class AbstractMongoDao<E> : MongoDao<E> {

    /**
     * 实体类Class
     */
    @Suppress("UNCHECKED_CAST")
    protected open val classType = (this.javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<E>

    /**
     * 集合名称
     */
    protected open val collectionName: String by lazy { determineCollectionName() }

    fun findOne(query: Query): E? {
        return findOne(query, classType)
    }

    fun find(query: Query): List<E> {
        return find(query, classType)
    }

    override fun <T> findOne(query: Query, clazz: Class<T>): T? {
        logger.debug("Mongo Dao findOne: [$query] [$clazz]")
        return determineMongoTemplate().findOne(query, clazz, determineCollectionName(query))
    }

    override fun <T> find(query: Query, clazz: Class<T>): List<T> {
        logger.debug("Mongo Dao find: [$query]")
        return determineMongoTemplate().find(query, clazz, determineCollectionName(query))
    }

    override fun insert(entity: E): E {
        logger.debug("Mongo Dao insert: [$entity]")
        return determineMongoTemplate().insert(entity, determineCollectionName(entity))
    }

    override fun save(entity: E): E {
        logger.debug("Mongo Dao save: [$entity]")
        return determineMongoTemplate().save(entity, determineCollectionName(entity))
    }

    override fun remove(query: Query): DeleteResult {
        logger.debug("Mongo Dao delete: [$query]")
        return determineMongoTemplate().remove(query, classType, determineCollectionName(query))
    }

    override fun updateFirst(query: Query, update: Update): UpdateResult {
        logger.debug("Mongo Dao updateFirst: [$query], [$update]")
        return determineMongoTemplate().updateFirst(query, update, determineCollectionName(query))
    }

    override fun updateMulti(query: Query, update: Update): UpdateResult {
        logger.debug("Mongo Dao updateMulti: [$query], [$update]")
        return determineMongoTemplate().updateMulti(query, update, determineCollectionName(query))
    }

    override fun upsert(query: Query, update: Update): UpdateResult {
        logger.debug("Mongo Dao upsert: [$query], [$update]")
        return determineMongoTemplate().upsert(query, update, determineCollectionName(query))
    }

    override fun count(query: Query): Long {
        logger.debug("Mongo Dao count: [$query]")
        return determineMongoTemplate().count(query, determineCollectionName(query))
    }

    override fun exists(query: Query): Boolean {
        logger.debug("Mongo Dao exists: [$query]")
        return determineMongoTemplate().exists(query, determineCollectionName(query))
    }

    override fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): AggregationResults<O> {
        logger.debug("Mongo Dao aggregate: [$aggregation], outputType: [$outputType]")
        return determineMongoTemplate().aggregate(aggregation, determineCollectionName(aggregation), outputType)
    }

    protected open fun determineCollectionName(): String {
        var collectionName: String? = null
        if (classType.isAnnotationPresent(Document::class.java)) {
            val document = classType.getAnnotation(Document::class.java)
            collectionName = document.collection
        }

        return if (collectionName.isNullOrEmpty()) MongoCollectionUtils.getPreferredCollectionName(classType) else collectionName
    }

    abstract fun determineMongoTemplate(): MongoTemplate

    abstract fun determineCollectionName(entity: E): String

    abstract fun determineCollectionName(query: Query): String

    abstract fun determineCollectionName(aggregation: Aggregation): String

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractMongoDao::class.java)
    }
}
