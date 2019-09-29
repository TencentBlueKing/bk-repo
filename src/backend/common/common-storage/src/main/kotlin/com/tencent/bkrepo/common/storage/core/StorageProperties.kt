package com.tencent.bkrepo.common.storage.core

/**
 * 存储基本配置属性
 *
 * @author: carrypan
 * @date: 2019-09-25
 */
abstract class StorageProperties {

        open var localCache: LocalCache = LocalCache()

        open var clientCache: ClientCache = ClientCache()

        open lateinit var credentials: ClientCredentials

        class LocalCache {
                /**
                 * 本地文件缓存开关
                 */
                var enabled: Boolean = true
                /**
                 * 存放缓存文件的本地目录
                 */
                var path: String = "/data/cached"
                /**
                 * 缓存文件时间，单位秒
                 */
                var period: Long = 60 * 60L
        }

        class ClientCache {
                /**
                 * 客户端缓存开关
                 */
                var enabled: Boolean = false
                /**
                 * 客户端缓存池大小
                 */
                var size: Long = 0L
        }
}
