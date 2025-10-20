package com.tencent.bkrepo.common.api.mongo

/**
 * 分表字段
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardingKeys(
    /**
     * 分表字段
     */
    val columns: Array<String> = [],
    /**
     * 分表数，power of 2
     */
    val count: Int = 1
)
