package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库 缓存配置
 * @author: carrypan
 * @date: 2019/11/26
 */
class RemoteCacheConfiguration(
    // 是否开启缓存
    val cacheEnabled: Boolean = true,
    // 构件缓存时间，单位分钟，负数表示永久缓存
    val cachePeriod: Long = 60
)
