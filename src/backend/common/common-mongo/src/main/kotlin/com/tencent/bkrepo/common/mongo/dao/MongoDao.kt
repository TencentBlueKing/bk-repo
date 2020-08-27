package com.tencent.bkrepo.common.mongo.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * mongo db 数据访问层接口
 */
interface MongoDao<E> {

    /**
     * 通过查询对象查询单条文档，返回元素类型由clazz指定
     */
    fun <T> findOne(query: Query, clazz: Class<T>): T?

    /**
     * 通过查询对象查询文档集合，返回元素类型由clazz指定
     */
    fun <T> find(query: Query, clazz: Class<T>): List<T>

    /**
     * 新增文档到数据库的集合中
     */
    fun insert(entity: E): E

    /**
     * 新增文档到数据库的集合中
     */
    fun save(entity: E): E

    /**
     * 更新单条文档
     */
    fun updateFirst(query: Query, update: Update): UpdateResult

    /**
     * 更新文档
     */
    fun updateMulti(query: Query, update: Update): UpdateResult

    /**
     * update or insert
     */
    fun upsert(query: Query, update: Update): UpdateResult

    /**
     * 统计数量
     */
    fun count(query: Query): Long

    /**
     * 判断文档是否存在
     */
    fun exists(query: Query): Boolean

    /**
     * 删除文档
     */
    fun remove(query: Query): DeleteResult

    /**
     * 文档聚合操作
     */
    fun <O> aggregate(aggregation: Aggregation, outputType: Class<O>): AggregationResults<O>
}
