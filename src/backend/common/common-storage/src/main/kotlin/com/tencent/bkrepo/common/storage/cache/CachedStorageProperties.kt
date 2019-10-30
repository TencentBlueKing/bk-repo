package com.tencent.bkrepo.common.storage.cache

import com.tencent.bkrepo.common.storage.core.StorageProperties

/**
 * 支持缓存的存储属性
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
abstract class CachedStorageProperties : StorageProperties() {
    open lateinit var cache: FileCacheProperties
}
