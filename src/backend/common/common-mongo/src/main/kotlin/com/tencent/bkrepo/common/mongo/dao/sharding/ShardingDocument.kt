package com.tencent.bkrepo.common.mongo.dao.sharding

/**
 * 分表Document
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardingDocument(
    /**
     * collection name
     */
    val collection: String = ""
)
