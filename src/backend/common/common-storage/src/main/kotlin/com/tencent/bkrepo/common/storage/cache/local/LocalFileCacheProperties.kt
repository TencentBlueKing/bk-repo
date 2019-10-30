package com.tencent.bkrepo.common.storage.cache.local

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 本地文件缓存属性
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
@ConfigurationProperties("storage.cache.local")
class LocalFileCacheProperties {
    /**
     * 存放缓存文件的本地目录
     */
    var path: String = "/data/cached"
    /**
     * 缓存文件时间，单位秒。默认12小时
     */
    var expires: Long = 60 * 60 * 12L
}
