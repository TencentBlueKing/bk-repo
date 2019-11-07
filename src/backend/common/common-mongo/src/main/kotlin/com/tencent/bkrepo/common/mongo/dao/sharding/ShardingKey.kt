package com.tencent.bkrepo.common.mongo.dao.sharding

/**
 * 分表字段
 *
 * @author: carrypan
 * @date: 2019/11/6
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ShardingKey(
    /**
     * 分表字段
     */
    val column: String = "",
    /**
     * 分表数，power of 2
     */
    val count: Int = 256
)
