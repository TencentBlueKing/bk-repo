package com.tencent.bkrepo.common.mongo.dao.sharding

/**
 * 分表Document
 *
 * @author: carrypan
 * @date: 2019/11/6
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardingDocument(
    /**
     * collection name
     */
    val collection: String = ""
)
