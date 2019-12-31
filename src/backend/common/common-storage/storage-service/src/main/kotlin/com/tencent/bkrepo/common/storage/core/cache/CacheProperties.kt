package com.tencent.bkrepo.common.storage.core.cache

/**
 * 本地文件缓存属性
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
data class CacheProperties(
    /**
     * 缓存开关
     */
    var enabled: Boolean = true,
    /**
     * 存放缓存文件的本地目录
     */
    var path: String = "/data/cached",
    /**
     * 缓存文件时间，单位小时。小于或等于0则永久存储
     */
    var expireHours: Int = -1
)
