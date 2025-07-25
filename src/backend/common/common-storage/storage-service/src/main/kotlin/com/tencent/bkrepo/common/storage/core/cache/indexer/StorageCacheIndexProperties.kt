/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core.cache.indexer

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 存储层缓存文件索引配置
 */
@ConfigurationProperties("storage.cache.index")
data class StorageCacheIndexProperties(
    var enabled: Boolean = false,
    /**
     * 索引器类型
     */
    var type: String = CACHE_TYPE_REDIS_SLRU,
    /**
     * 使用基于Redis实现的索引器时作为hashTag
     */
    var hashTag: String? = null,
    /**
     * 是否执行缓存淘汰
     */
    var evict: Boolean = true,
    /**
     * 是否同步已存在的缓存文件
     * 可能存在缓存条目被淘汰后，由于缓存保留策略或其他原因无法删除缓存文件，此时如果开启缓存同步会再次将被淘汰的缓存条目加入到缓存索引器中
     */
    var syncExistedCacheFile: Boolean = true,
    /**
     * 一次淘汰中最多淘汰的缓存条目数
     */
    var maxEvictCount: Int = 1000
) {
    companion object {
        /**
         * 基于Redis实现的LRU
         */
        const val CACHE_TYPE_REDIS_LRU = "REDIS_LRU"

        /**
         * 基于Redis实现的SLRU
         */
        const val CACHE_TYPE_REDIS_SLRU = "REDIS_SLRU"
    }
}
