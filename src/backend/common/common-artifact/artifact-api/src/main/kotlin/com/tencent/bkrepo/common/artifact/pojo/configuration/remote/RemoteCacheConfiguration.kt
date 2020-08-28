package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库 缓存配置
 */
data class RemoteCacheConfiguration(
    /**
     * 是否开启缓存
     */
    var enabled: Boolean = true,
    /**
     * 构件缓存时间，单位分钟，0或负数表示永久缓存
     */
    var expiration: Long = -1L
)
